/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2018.                            (c) 2018.
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
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class IdentityTypeTest
{
    private final static Logger log = Logger.getLogger(IdentityTypeTest.class);
    
    @BeforeClass
    public static void setUpClass()
    {
        Log4jInit.setLevel("ca.nrc.cadc.ac", Level.INFO);
    }
    /**
     * Test of values method, of class IdentityType.
     */
    @Test
    public void testValues()
    {
        IdentityType[] expResult = new IdentityType[]
        {
            IdentityType.X500, IdentityType.OPENID, 
            IdentityType.USERNAME, IdentityType.USERID,
            IdentityType.CADC, IdentityType.NUMERICID,
            IdentityType.COOKIE, IdentityType.ENTRY_DN
        };
        IdentityType[] result = IdentityType.values();
        assertArrayEquals(expResult, result);
    }

    /**
     * Test of valueOf method, of class IdentityType.
     */
    @Test
    public void testValueOf()
    {
        assertEquals(IdentityType.X500, IdentityType.valueOf("X500"));
        assertEquals(IdentityType.OPENID, IdentityType.valueOf("OPENID"));
        assertEquals(IdentityType.USERNAME, IdentityType.valueOf("USERNAME"));
        assertEquals(IdentityType.CADC, IdentityType.valueOf("CADC"));
        assertEquals(IdentityType.COOKIE, IdentityType.valueOf("COOKIE"));
        assertEquals(IdentityType.USERID, IdentityType.valueOf("USERID"));
        assertEquals(IdentityType.NUMERICID, IdentityType.valueOf("NUMERICID"));
    }

    /**
     * Test of toValue method, of class IdentityType.
     */
    @Test
    public void testToValue()
    {
        try
        {
            IdentityType.toValue("foo");
            fail("invalid value should throw IllegalArgumentException");
        }
        catch (IllegalArgumentException ignore) {}
        
        assertEquals(IdentityType.X500, IdentityType.toValue("X500"));
        assertEquals(IdentityType.OPENID, IdentityType.toValue("OpenID"));
        assertEquals(IdentityType.USERID, IdentityType.toValue("userID"));
        assertEquals(IdentityType.NUMERICID, IdentityType.toValue("numericID"));
        assertEquals(IdentityType.USERNAME, IdentityType.toValue("HTTP"));
        assertEquals(IdentityType.CADC, IdentityType.toValue("CADC"));
        assertEquals(IdentityType.COOKIE, IdentityType.toValue("sessionID"));
    }

    /**
     * Test of getValue method, of class IdentityType.
     */
    @Test
    public void testGetValue()
    {
        assertEquals("X500", IdentityType.X500.getValue());
        assertEquals("OpenID", IdentityType.OPENID.getValue());
        assertEquals("HTTP", IdentityType.USERNAME.getValue());
        assertEquals("CADC", IdentityType.CADC.getValue());
        assertEquals("sessionID", IdentityType.COOKIE.getValue());
        assertEquals("userID", IdentityType.USERID.getValue());
        assertEquals("numericID", IdentityType.NUMERICID.getValue());
    }

    /**
     * Test of checksum method, of class IdentityType.
     */
    @Test
    public void testChecksum()
    {
        assertEquals("X500".hashCode(), IdentityType.X500.checksum());
        assertEquals("OpenID".hashCode(), IdentityType.OPENID.checksum());
        assertEquals("HTTP".hashCode(), IdentityType.USERNAME.checksum());
        assertEquals("CADC".hashCode(), IdentityType.CADC.checksum());
        assertEquals("sessionID".hashCode(), IdentityType.COOKIE.checksum());
        assertEquals("userID".hashCode(), IdentityType.USERID.checksum());
        assertEquals("numericID".hashCode(), IdentityType.NUMERICID.checksum());
    }
    
}
