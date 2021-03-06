/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.directory.ldap;

import java.io.File;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.store.LdifLoadFilter;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeHarness;

/**
 * @author <a href="ogrisel@nuxeo.com">Olivier Grisel</a>
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.directory.sql", //
        "org.nuxeo.ecm.directory.ldap.tests", //
})
@LocalDeploy({ "org.nuxeo.ecm.directory.ldap.tests:ldap-test-setup/DirectoryTypes.xml",
        "org.nuxeo.ecm.directory.ldap.tests:ldap-test-setup/DirectoryService.xml",
        "org.nuxeo.ecm.directory.ldap.tests:ldap-test-setup/LDAPDirectoryFactory.xml",
        "org.nuxeo.ecm.directory.ldap.tests:TestSQLDirectories.xml", })
public abstract class LDAPDirectoryTestCase {

    private static final Log log = LogFactory.getLog(LDAPDirectoryTestCase.class);

    protected MockLdapServer server;

    // change this flag to use an external LDAP directory instead of the
    // non networked default ApacheDS implementation
    public static final boolean USE_EXTERNAL_TEST_LDAP_SERVER = false;

    // change this flag in case the external LDAP server considers the
    // posixGroup class structural
    public static final boolean POSIXGROUP_IS_STRUCTURAL = true;

    // change this flag if your test server has support for dynamic groups
    // through the groupOfURLs objectclass, eg for OpenLDAP:
    // http://www.ldap.org.br/modules/ldap/files/files///dyngroup.schema
    public static final boolean HAS_DYNGROUP_SCHEMA = false;

    public static final String INTERNAL_SERVER_SETUP_UPPER_ID = "TestDirectoriesWithInternalApacheDS-override-upper-id.xml";

    // These variables are changed in subclasses
    public String EXTERNAL_SERVER_SETUP = "TestDirectoriesWithExternalOpenLDAP.xml";

    public String INTERNAL_SERVER_SETUP = "TestDirectoriesWithInternalApacheDS.xml";

    public String EXTERNAL_SERVER_SETUP_OVERRIDE = "TestDirectoriesWithExternalOpenLDAP-override.xml";

    public String INTERNAL_SERVER_SETUP_OVERRIDE = "TestDirectoriesWithInternalApacheDS-override.xml";

    @Inject
    protected RuntimeHarness runtimeHarness;

    public List<String> getLdifFiles() {
        List<String> ldifFiles = new ArrayList<String>();
        ldifFiles.add("sample-users.ldif");
        ldifFiles.add("sample-groups.ldif");
        if (HAS_DYNGROUP_SCHEMA) {
            ldifFiles.add("sample-dynamic-groups.ldif");
        }
        return ldifFiles;
    }

    @Before
    public void setUp() throws Exception {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            runtimeHarness.deployContrib("org.nuxeo.ecm.directory.ldap.tests", EXTERNAL_SERVER_SETUP);
        } else {
            runtimeHarness.deployContrib("org.nuxeo.ecm.directory.ldap.tests", INTERNAL_SERVER_SETUP);
            server = new MockLdapServer(new File(Framework.getRuntime().getHome(), "ldap"));
            getLDAPDirectory("userDirectory").setTestServer(server);
            getLDAPDirectory("groupDirectory").setTestServer(server);
        }
        LDAPSession session = (LDAPSession) getLDAPDirectory("userDirectory").getSession();
        try {
            DirContext ctx = session.getContext();
            for (String ldifFile : getLdifFiles()) {
                loadDataFromLdif(ldifFile, ctx);
            }
        } finally {
            session.close();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (USE_EXTERNAL_TEST_LDAP_SERVER) {
            LDAPSession session = (LDAPSession) getLDAPDirectory("userDirectory").getSession();
            try {
                DirContext ctx = session.getContext();
                destroyRecursively("ou=people,dc=example,dc=com", ctx, -1);
                destroyRecursively("ou=groups,dc=example,dc=com", ctx, -1);
            } finally {
                session.close();
            }
            runtimeHarness.undeployContrib("org.nuxeo.ecm.directory.ldap.tests", EXTERNAL_SERVER_SETUP);
        } else {
            if (server != null) {
                try {
                    server.shutdownLdapServer();
                } finally {
                    server = null;
                }
            }
            runtimeHarness.undeployContrib("org.nuxeo.ecm.directory.ldap.tests", INTERNAL_SERVER_SETUP);
        }
    }

    protected static void loadDataFromLdif(String ldif, DirContext ctx) {
        List<LdifLoadFilter> filters = new ArrayList<LdifLoadFilter>();
        LdifFileLoader loader = new LdifFileLoader(ctx, new File(ldif), filters,
                Thread.currentThread().getContextClassLoader());
        loader.execute();
    }

    protected void destroyRecursively(String dn, DirContext ctx, int limit) throws NamingException {
        if (limit == 0) {
            log.warn("Reach recursion limit, stopping deletion at" + dn);
            return;
        }
        SearchControls scts = new SearchControls();
        scts.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        String providerUrl = (String) ctx.getEnvironment().get(Context.PROVIDER_URL);
        NamingEnumeration<SearchResult> children = ctx.search(dn, "(objectClass=*)", scts);
        try {
            while (children.hasMore()) {
                SearchResult child = children.next();
                String subDn = child.getName();
                if (!USE_EXTERNAL_TEST_LDAP_SERVER && subDn.endsWith(providerUrl)) {
                    subDn = subDn.substring(0, subDn.length() - providerUrl.length() - 1);
                } else {
                    subDn = subDn + ',' + dn;
                }
                destroyRecursively(subDn, ctx, limit);
            }
        } catch (SizeLimitExceededException e) {
            log.warn("SizeLimitExceededException: trying again on partial results " + dn);
            if (limit == -1) {
                limit = 100;
            }
            destroyRecursively(dn, ctx, limit - 1);
        }
        ctx.destroySubcontext(dn);
    }

    public static LDAPDirectory getLDAPDirectory(String name) throws DirectoryException {
        LDAPDirectoryFactory factory = (LDAPDirectoryFactory) Framework.getRuntime().getComponent(
                LDAPDirectoryFactory.NAME);
        return (LDAPDirectory) factory.getDirectory(name);
    }

    /**
     * Method to create a X509 certificate used to test the creation and the update of an entry in the ldap.
     *
     * @return A X509 certificate
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws IllegalStateException
     * @since 5.9.3
     */
    protected X509Certificate createCertificate(String dnNameStr) throws NoSuchAlgorithmException,
            CertificateException, InvalidKeyException, IllegalStateException, SignatureException {
        X509Certificate cert = null;

        // Parameters used to define the certificate
        // yesterday
        Date validityBeginDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        // in 2 years
        Date validityEndDate = new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000);

        // Generate the key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Define the content of the certificate
        X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
        X500Principal dnName = new X500Principal(dnNameStr);

        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(dnName);
        certGen.setIssuerDN(dnName); // use the same
        certGen.setNotBefore(validityBeginDate);
        certGen.setNotAfter(validityEndDate);
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSA");

        cert = certGen.generate(keyPair.getPrivate());

        return cert;
    }
}
