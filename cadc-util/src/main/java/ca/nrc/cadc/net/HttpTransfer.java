/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2016.                            (c) 2016.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.net;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.net.event.ProgressListener;
import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.net.event.TransferListener;
import ca.nrc.cadc.util.FileMetadata;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.StringUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Base class for HTTP transfers.
 * @author pdowler
 */
public abstract class HttpTransfer implements Runnable
{
    private static Logger log = Logger.getLogger(HttpTransfer.class);

    /**
     * Not documented in HttpURLConnection.  Represents a locked resource,
     * which primarily relates to WebDAV.
     */
    static final int HTTP_LOCKED = 423;

    public static String DEFAULT_USER_AGENT;
    public static final String CADC_CONTENT_LENGTH_HEADER = "X-CADC-Content-Length";
    public static final String CADC_STREAM_HEADER = "X-CADC-Stream";
    public static final String CADC_PARTIAL_READ_HEADER = "X-CADC-Partial-Read";

    public static final String SERVICE_RETRY = "Retry-After";

    public static final int DEFAULT_BUFFER_SIZE = 8*1024; // 8KB
    // note: thecombiantion of a large buffer, small-ish streamed put w/ no
    // content-length, and tomcat6 fails, plus apache+tomcat seem to have some
    // limits at 8k anyway

    public static enum RetryReason
    {
        /**
         * Never retry.
         */
        NONE(0),

        /**
         * Retry when the server says to do so (503 + Retry-After).
         */
        SERVER(1),

        /**
         * Retry for all failures deemed transient (undocumented). This option
         * includes the SERVER reasons.
         */
        TRANSIENT(2),

        /**
         * Retry for all failures (yes, even 4xx failures). This option includes
         * the TRANSIENT reasons.
         */
        ALL(3);

        private int value;

        private RetryReason(int val) { this.value = val; }
    }

    /**
     * The maximum retry delay (128 seconds).
     */
    public static final int MAX_RETRY_DELAY = 128;
    public static final int DEFAULT_RETRY_DELAY = 30;

    protected int maxRetries = 3;
    protected int retryDelay = 1; // 1, 2, 4 sec
    protected RetryReason retryReason = RetryReason.TRANSIENT;

    protected int numRetries = 0;
    protected int curRetryDelay = 0; // scaled after each retry

    protected int bufferSize = DEFAULT_BUFFER_SIZE;
    protected OverwriteChooser overwriteChooser;
    protected ProgressListener progressListener;
    protected TransferListener transferListener;
    protected boolean fireEvents = false;
    protected boolean fireCancelOnce = true;

    protected List<HttpRequestProperty> requestProperties;
    protected String userAgent;
    protected boolean use_nio = false; // throughput not great, needs work before use
    protected boolean logIO = false;
    protected long writeTime = 0L;
    protected long readTime = 0L;

    protected boolean go;
    protected Thread thread;

    // state set by caller
    protected URL remoteURL;
    protected File localFile;

    // state that observer(s) might be interested in
    public String eventID = null;
    public Throwable failure;

    protected boolean followRedirects = false;
    protected URL redirectURL;
    protected int responseCode = -1;

    private SSLSocketFactory sslSocketFactory;

    static
    {
        String jv = "Java " + System.getProperty("java.version") + ";" + System.getProperty("java.vendor");
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        DEFAULT_USER_AGENT = "OpenCADC/" + HttpTransfer.class.getName() + "/" + jv + "/" + os;
    }

    protected HttpTransfer(boolean followRedirects)
    {
        this.followRedirects = followRedirects;
        this.go = true;
        this.requestProperties = new ArrayList<HttpRequestProperty>();
        this.userAgent = DEFAULT_USER_AGENT;

        String bsize = null;
        try
        {
            bsize = System.getProperty(HttpTransfer.class.getName() + ".bufferSize");
            if (bsize != null)
            {
                int mult = 1;
                String sz = bsize;
                bsize = bsize.trim();
                if (bsize.endsWith("k"))
                {
                    mult = 1024;
                    sz = bsize.substring(0, bsize.length() - 1);
                }
                else if (bsize.endsWith("m"))
                {
                    mult = 1024*1024;
                    sz = bsize.substring(0, bsize.length() - 1);
                }
                this.bufferSize = mult * Integer.parseInt(sz);
            }
        }
        catch(NumberFormatException warn)
        {
            log.warn("invalid buffer size: " + bsize + ", using default " + DEFAULT_BUFFER_SIZE);
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        }
        log.debug("bufferSize: " + bufferSize);
    }

    /**
     * Set the current following redirects behaviour.
     *
     * @param followRedirects
     */
    public void setFollowRedirects(boolean followRedirects)
    {
        this.followRedirects = followRedirects;
    }

    /**
     * If the response resulted in a redirect that wasn't followed, it
     * can be retrieved here.
     */
    public URL getRedirectURL()
    {
        return redirectURL;
    }


    /**
     * Enable retry (maxRetries &gt; 0) and set the maximum number of times
     * to retry before failing. The default is to retry only when the server
     * says to do so (e.g. 503 + Retry-After).
     *
     * @param maxRetries
     */
    public void setMaxRetries(int maxRetries)
    {
        this.maxRetries = maxRetries;
    }

    /**
     * Configure retry of failed transfers. If configured to retry, transfers will
     * be retried when failing for the reason which match the specified reason up
     * to maxRetries times. The retryDelay (in seconds) is scaled by a factor of two
     * for each subsequent retry (eg, 2, 4, 8, ...) in cases where the server response
     * does not provide a retry delay.
     * <p>
     * The default reason is RetryReason.SERVER.
     * </p>
     * 
     * @param maxRetries number of times to retry, 0 or negative to disable retry
     * @param retryDelay delay in seconds before retry
     * @param reason
     */
    public void setRetry(int maxRetries, int retryDelay, RetryReason reason)
    {
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
        this.retryReason = reason;
    }

    public URL getURL() { return remoteURL; }

    /**
     * Set the buffer size in bytes. Transfers allocate a buffer to use in
     * the IO loop and also wrap BufferedInputStream and BufferedOutputStream
     * around the underlying InputStream and OutputStream (if they are not already
     * buffered).
     * <p>
     * Note: The buffer size can also be set with the system property
     * <code>ca.nrc.cadc.net.HttpTransfer.bufferSize</code> which is an integer
     * number of bytes. The value may be specified in KB by appending 'k' or MB by
     * appending 'm' (e.g. 16k or 2m).
     * </p>
     * 
     * @param bufferSize
     */
    public void setBufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    public int getBufferSize()
    {
        return bufferSize;
    }


    public void setUserAgent(String userAgent)
    {
        this.userAgent = userAgent;
        if (userAgent == null)
            this.userAgent = DEFAULT_USER_AGENT;
    }
    
    /**
     * If enabled, the time spent on the buffer reading and writing will
     * be available through getIOReadTime() and getIOWriteTime().
     * @param logIO
     */
    public void setLogIO(boolean logIO)
    {
        this.logIO = logIO;
    }
    
    /*
     * If logIO is set to true, return the time in milliseconds spent
     * reading from the input stream.  Otherwise, return null.
     */
    public Long getIOReadTime()
    {
        if (logIO)
            return readTime;
        return null;
    }
    
    /*
     * If logIO is set to true, return the time in milliseconds spent
     * writing to the output stream.  Otherwise, return null.
     */
    public Long getIOWriteTime()
    {
        if (logIO)
            return writeTime;
        return null;
    }

    /**
     * Set additional request headers. Do not set the same value twice by using this
     * method and the specific set methods (like setUserAgent, setContentType, etc) in this
     * class or subclasses.
     *
     * @param header
     * @param value
     */
    public void setRequestProperty(String header, String value)
    {
        requestProperties.add(new HttpRequestProperty(header, value));
    }

    /**
     * Set additional request properties. Adds all the specified properties to
     * those set with setRequestProperty (if any).
     *
     * @see setRequestProperty
     * @param props
     */
    public void setRequestProperties(List<HttpRequestProperty> props)
    {
        if (props != null)
        {
            log.debug("add request properties: " + props.size());
            this.requestProperties.addAll(props);
        }
    }

    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory)
    {
        this.sslSocketFactory = sslSocketFactory;
    }

    public SSLSocketFactory getSSLSocketFactory()
    {
        return this.sslSocketFactory;
    }

    public void setOverwriteChooser(OverwriteChooser overwriteChooser) { this.overwriteChooser = overwriteChooser; }

    public void setProgressListener(ProgressListener listener)
    {
        this.progressListener = listener;
        fireEvents = (progressListener != null || transferListener != null);
    }

    public void setTransferListener(TransferListener listener)
    {
        this.transferListener = listener;
        fireEvents = (progressListener != null || transferListener != null);
    }

    /**
     * Get the total number of retries performed.
     *
     * @return number of retries performed
     */
    public int getRetriesPerformed()
    {
        return numRetries;
    }

    /**
     * Get the ultimate (possibly after retries) HTTP response code.
     *
     * @return HTTP response code or -1 if no HTTP call made
     */
    public int getResponseCode()
    {
        return responseCode;
    }


    /**
     * If the transfer ultimately failed, this will return the last failure.

     * @return the last failure, or null if successful
     */
    public Throwable getThrowable() { return failure; }

    public void terminate()
    {
        this.fireEvents = false; // prevent run() and future calls to terminate from firing the CANCELLED event
        this.go = false;
        synchronized(this) // other synchronized block in in the finally part of run()
        {
            if (thread != null)
            {
                // give it a poke just in case it is blocked/slow
                log.debug("terminate(): interrupting " + thread.getName());
                try
                {
                    thread.interrupt();
                }
                catch(Throwable ignore) { }
            }
        }
        fireCancelledEvent();
        this.fireCancelOnce = false;
    }

    /**
     *  Determine if the failure was transient according to the config options.
     * @param code status code
     * @param msg message
     * @param conn connection
     * @throws TransientException to cause retry
     */
    protected void checkTransient(int code, String msg, HttpURLConnection conn)
        throws TransientException
    {
        if (RetryReason.NONE.equals(retryReason))
            return;

        boolean trans = false;
        int dt = 0;

        // try to get the retry delay from the response
        if (code == HttpURLConnection.HTTP_UNAVAILABLE)
        {
            msg = "server busy";
            String retryAfter = conn.getHeaderField(SERVICE_RETRY);
            log.debug("got " + HttpURLConnection.HTTP_UNAVAILABLE + " with " + SERVICE_RETRY + ": " + retryAfter);
            if (StringUtil.hasText(retryAfter))
            {
                try
                {
                    dt = Integer.parseInt(retryAfter);
                    trans = true; // retryReason==SERVER satisfied
                    if (dt > MAX_RETRY_DELAY)
                        dt = MAX_RETRY_DELAY;
                }
                catch(NumberFormatException nex)
                {
                    log.warn(SERVICE_RETRY + " after a 503 was not a number: " + retryAfter + ", ignoring");
                }
            }
        }

        if (RetryReason.TRANSIENT.equals(retryReason))
        {
            switch(code)
            {
                case HttpURLConnection.HTTP_UNAVAILABLE:
                case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
                case HttpURLConnection.HTTP_PRECON_FAILED:      // ??
                case HttpURLConnection.HTTP_PAYMENT_REQUIRED:   // maybe it will become free :-)
                    trans = true;
            }
        }
        if (RetryReason.ALL.equals(retryReason))
        {
            trans = true;
        }

        if (trans && numRetries < maxRetries)
        {
            if (dt == 0)
            {
                if (curRetryDelay == 0)
                    curRetryDelay = retryDelay;
                if (curRetryDelay > 0)
                {
                    dt = curRetryDelay;
                    curRetryDelay *= 2;
                }
                else
                    dt = DEFAULT_RETRY_DELAY;
            }
            numRetries++;
            throw new TransientException(msg, dt);
        }
    }

    protected void findEventID(HttpURLConnection conn)
    {
        String eventHeader = null;
        if (transferListener != null)
            eventHeader = transferListener.getEventHeader();
        if (eventHeader != null)
            this.eventID = conn.getHeaderField(eventHeader);
    }

    private void fireCancelledEvent()
    {
        if (fireCancelOnce)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, localFile, TransferEvent.CANCELLED);
            fireEvent(e);
        }
    }
    private void fireEvent(TransferEvent e)
    {
        log.debug("fireEvent: " + e);
        if (transferListener != null)
            transferListener.transferEvent(e);
        if (progressListener != null)
            progressListener.transferEvent(e);
    }

    protected void fireEvent(int state)
    {
        fireEvent(localFile, state);
    }

    protected void fireEvent(File file, int state)
    {
        fireEvent(file, state, null);
    }

    protected void fireEvent(File file, int state, FileMetadata meta)
    {
        if (fireEvents)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, file, state);
            e.setFileMetadata(meta);
            fireEvent(e);
        }
    }

    protected void fireEvent(Throwable t)
    {
        fireEvent(localFile, t);
    }

    protected void fireEvent(File file, Throwable t)
    {
        if (fireEvents)
        {
            TransferEvent e = new TransferEvent(this, eventID, remoteURL, file, t);
            fireEvent(e);
        }
    }

    /**
     * @param sslConn
     */
    protected void initHTTPS(HttpsURLConnection sslConn)
    {
        if (sslSocketFactory == null) // lazy init
        {
            log.debug("initHTTPS: lazy init");
            AccessControlContext ac = AccessController.getContext();
            Subject s = Subject.getSubject(ac);
            this.sslSocketFactory = SSLUtil.getSocketFactory(s);
        }
        if (sslSocketFactory != null)
        {
            log.debug("setting SSLSocketFactory on " + sslConn.getClass().getName());
            sslConn.setSSLSocketFactory(sslSocketFactory);
        }
    }

    /**
     * Perform the IO loop. This method reads from the input and writes to the output using an
     * internal byte array of the specified size.
     *
     * @param istream
     * @param ostream
     * @param sz
     * @param startingPos for resumed transfers, this effects the reported value seen by
     * the progressListener (if set)
     * @return string representation of the content md5sum
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    protected String ioLoop(InputStream istream, OutputStream ostream, int sz, long startingPos)
        throws IOException, InterruptedException
    {
        log.debug("ioLoop: using java.io with byte[] buffer size " + sz + " startingPos " + startingPos);
        long readStart = 0;
        long writeStart = 0;
        byte[] buf = new byte[sz];

        MessageDigest md5 = null;
        try { md5 = MessageDigest.getInstance("MD5"); }
        catch(NoSuchAlgorithmException oops)
        {
            log.warn("failed to create MessageDigest(MD5): " + oops);
        }
        
        int nb = 0;
        int nb2 = 0;
        long tot = startingPos; // non-zero for resumed transfer
        int n = 0;

        if (progressListener != null)
            progressListener.update(0, tot);

        while (nb != -1)
        {
            // check/clear interrupted flag and throw if necessary
            if ( Thread.interrupted() )
                throw new InterruptedException();

            if (logIO)
                readStart = System.currentTimeMillis();
            nb = istream.read(buf, 0, sz);
            if (logIO)
                readTime += System.currentTimeMillis() - readStart;
            
            if (nb != -1)
            {
                if (nb < sz/2)
                {
                    // try to get more data: merges a small chunk with a
                    // subsequent one to minimise write calls
                    if (logIO)
                        readStart = System.currentTimeMillis();
                    nb2 = istream.read(buf, nb, sz-nb);
                    if (logIO)
                        readTime += System.currentTimeMillis() - readStart;
                    if (nb2 > 0)
                        nb += nb2;
                }
                //log.debug("write buffer: " + nb);
                if (md5 != null)
                    md5.update(buf, 0, nb);
                if (logIO) 
                    writeStart = System.currentTimeMillis();
                ostream.write(buf, 0, nb);
                if (logIO)
                    writeTime += System.currentTimeMillis() - writeStart;
                tot += nb;
                if (progressListener != null)
                    progressListener.update(nb, tot);
            }
        }
        if (md5 != null)
        {
            byte[] md5sum = md5.digest();
            String ret = HexUtil.toHex(md5sum);
            return ret;
        }
        return null;
    }

    /**
     * Perform the IO loop using the nio library.
     *
     * @param istream
     * @param ostream
     * @param sz
     * @param startingPos
     * @throws IOException
     * @throws InterruptedException
     */
    protected void nioLoop(InputStream istream, OutputStream ostream, int sz, long startingPos)
        throws IOException, InterruptedException
    {
        // Note: If NIO is enabled, the logIO option should be added at
        // the same time (see ioLoop).
        
        log.debug("[Download] nioLoop: using java.nio with ByteBuffer size " + sz);
        // check/clear interrupted flag and throw if necessary
        if ( Thread.interrupted() )
            throw new InterruptedException();
        
        MessageDigest md5 = null;
        try { md5 = MessageDigest.getInstance("MD5"); }
        catch(NoSuchAlgorithmException oops)
        {
            log.warn("failed to create MessageDigest(MD5): " + oops);
        }

        ReadableByteChannel rbc = Channels.newChannel(istream);
        WritableByteChannel wbc = Channels.newChannel(ostream);

        long tot = startingPos; // non-zero for resumed transfer
        int count = 0;

        ByteBuffer buffer = ByteBuffer.allocate(sz);

        if (progressListener != null)
            progressListener.update(count, tot);

        while(count != -1)
        {
            // check/clear interrupted flag and throw if necessary
            if ( Thread.interrupted() )
                throw new InterruptedException();

            count = rbc.read(buffer);
            if (count != -1)
            {
                wbc.write((ByteBuffer)buffer.flip());
                buffer.flip();
                tot += count;
                if (progressListener != null)
                    progressListener.update(count, tot);
            }
        }
    }

    protected void setRequestSSOCookie(HttpURLConnection conn)
    {
        AccessControlContext acc = AccessController.getContext();
        Subject subj = Subject.getSubject(acc);
        if (subj != null)
        {
            Set<SSOCookieCredential> cookieCreds = subj
                    .getPublicCredentials(SSOCookieCredential.class);
            if ((cookieCreds != null) && (cookieCreds.size() > 0))
            {
                // grab the first cookie that matches the domain
                boolean found = false;
                for (SSOCookieCredential cookieCred : cookieCreds)
                {
                    if (conn.getURL().getHost().endsWith(cookieCred.getDomain()))
                    {
                        // HACK ("Pat Said") - this is rather horrenous, but in the java HTTP
                        // library, the cookie isn't sent with the redirect. But it doesn't flag it
                        // as a problem. This flags the problem early, allows us to detect attempts
                        // to send cookies + redirect via POST.
                        // GET (HttpDownload) works, and sends the cookies as expected.
                        if (followRedirects && "POST".equals(conn.getRequestMethod())) {
                            throw new UnsupportedOperationException("Attempt to follow redirect with cookies (POST).");
                        }

                        String cval = SSOCookieManager.DEFAULT_SSO_COOKIE_NAME
                                + "=\"" + cookieCred.getSsoCookieValue() + "\"";
                        conn.setRequestProperty("Cookie", cval);
                        log.debug("setRequestSSOCookie: " + cval);
                        found = true;
                        break;
                    }
                }
                if (!found)
                {
                    log.debug("setRequestSSOCookie: no cookie for domain: "
                            + conn.getURL().getHost());
                }
            }
            else
            {
                log.debug("setRequestSSOCookie: no cookie");
            }
        }
    }

    protected void setRequestHeaders(HttpURLConnection conn)
    {
        log.debug("custom request properties: " + requestProperties.size());
        for (HttpRequestProperty rp : requestProperties)
        {
            String p = rp.getProperty();
            String v = rp.getValue();
            log.debug("set request property: "+p+"="+v);
            conn.setRequestProperty(p, v);
        }
    }
}
