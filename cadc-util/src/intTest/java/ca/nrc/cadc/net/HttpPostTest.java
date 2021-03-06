/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2020.                            (c) 2020.
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

import ca.nrc.cadc.util.Log4jInit;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * Integration tests for HttpPost.  These tests use the TestServlet
 * available in project cadcTestServlet.
 * 
 * @author majorb
 *
 */
public class HttpPostTest
{
    private static Logger log = Logger.getLogger(HttpPostTest.class);
    
    static {
        Log4jInit.setLevel("ca.nrc.cadc.net", Level.INFO);
    }
    
    File srcFile;
    
    public HttpPostTest() throws Exception {
        File tmp = File.createTempFile("public" + HttpUploadTest.class.getSimpleName(), ".in");        
        FileWriter out = new FileWriter(tmp);
        out.append("sample input data");
        out.close();
        tmp.deleteOnExit();
        
        this.srcFile = tmp;
    }
    
    @Test
    public void testPostMap() throws Exception
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("IN", "foo");
        params.put("OPT", "bar");
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        HttpPost post = new HttpPost(new URL("https://httpbin.org/post"), params, bos);
        post.run();
        Assert.assertEquals(200, post.getResponseCode());
        Assert.assertNull("throwable", post.getThrowable());
        String str = bos.toString("UTF-8");
        log.info("output:\n" + str);
    }
    
    @Test
    public void testPostFileParam() throws Exception
    {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("IN", srcFile);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        HttpPost post = new HttpPost(new URL("https://httpbin.org/post"), params, bos);
        post.run();
        Assert.assertEquals(200, post.getResponseCode());
        Assert.assertNull("throwable", post.getThrowable());
        String str = bos.toString("UTF-8");
        log.info("output:\n" + str);
    }

    @Test
    public void testPostFileContent() throws Exception
    {
        FileContent fc = new FileContent("sample input data", "text/plain", Charset.forName("UTF-8"));
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        HttpPost post = new HttpPost(new URL("https://httpbin.org/post"), fc, true);
        post.run();
        Assert.assertEquals(200, post.getResponseCode());
        Assert.assertNull("throwable", post.getThrowable());
        String str = bos.toString("UTF-8");
        
        log.info("output:\n" + str);
    }
    
    
    
    //@Test
    public void testPostRedirect302() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("url", "https://www.example.net/");
        params.put("status_code", "302");
        
        HttpPost post = new HttpPost(new URL("https://httpbin.org/redirect-to"), params, false);
        post.run();
        Assert.assertEquals(302, post.getResponseCode());
        Assert.assertNull("throwable" + post.getThrowable());
        Assert.assertNotNull("redirect", post.getRedirectURL());
        log.info("redirect: " + post.getRedirectURL());
    }
}
