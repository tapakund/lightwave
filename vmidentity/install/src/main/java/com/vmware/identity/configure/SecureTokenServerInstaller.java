/* **********************************************************************
 * Copyright 2015 VMware, Inc.  All rights reserved.
 * *********************************************************************/

package com.vmware.identity.configure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.vmware.vim.sso.client.SecureTransformerFactory;
import com.vmware.vim.sso.client.XmlParserFactory;
import com.vmware.af.VmAfClient;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vmware.provider.VecsLoadStoreParameter;

public class SecureTokenServerInstaller implements IPlatformComponentInstaller {

    private static final String ID = "vmware-secure-token-service";
    private static final String Name = "VMware Secure Token Service";
    private static final String Description = "VMware Secure Token Service";

    private static final String SSL_ENABLED_ATTR= "SSLEnabled";
    private static final String SSL_ENABLED_PROTOCOL_ATTR="protocols";
    private static final String SSL_IMPLEMENTATION_NAME_ATTR="sslImplementationName";
    private static final String STORE_ATTR="store";
    private static final String KEYALIAS_ATTR="certificateKeyAlias";
    private static final String KEYSTORETYPE_ATTR="certificateKeystoreType";
    private static final String CONNECTOR = "Connector";
    private static final String SSL_HOST_CONFIG = "SSLHostConfig";
    private static final String CERTIFICATE_ATTR = "Certificate";
    private static final String STS_HEALTH_ENDPOINT = "/isAvailable";
    private static final String VKS_KEYSTORE_INSTANCE = "VKS";
    private static final String VKS_KEYSTORE_NAME = "TRUSTED_ROOTS";
    private static final long MAX_TIME_TO_WAIT_MILLIS = 120000 ; // 2 minutes
    private static final long WAIT_TIME_PER_ITERATION = 5000; // 5 seconds

    private static final Logger log = LoggerFactory
            .getLogger(SecureTokenServerInstaller.class);

    private String hostnameURL = null;
    private VmIdentityParams params = null;
    private String sslEnabledProtocols = "";
    private String storename = "";
    private String sslImplementationName="";
    private String keyAlias ="";
    private String keyStoreType ="";

    private static final XmlParserFactory xmlParserFactory = XmlParserFactory.Factory.createSecureXmlParserFactory();

    public SecureTokenServerInstaller(VmIdentityParams installParams) {
        params = installParams;
    }

    @Override
    public void install() throws Exception {
        initialize();

        log.info("Configuring STS");
        configureSTS();

        // Only on Windows the STS service has to be installed as a service
        installInstAsWinService();

        startSTSService();
        checkSTSHealth();
    }

    private void initialize() {
        if (hostnameURL == null) {
            this.hostnameURL = VmAfClientUtil.getHostnameURL();
        }
    }

    private void startSTSService() throws SecureTokenServerInstallerException {

        ProcessBuilder pb = new ProcessBuilder(InstallerUtils
                .getInstallerHelper().getSTSServiceStartCommand());
        pb.redirectErrorStream(true);

        String logFile = InstallerUtils.getInstallerHelper()
                .getIDMServiceLogFile();
        File log = new File(logFile);
        pb.redirectOutput(Redirect.appendTo(log));

        int exitCode = -1;
        try {
            final Process p = pb.start();
            exitCode = p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new SecureTokenServerInstallerException(
                    "Failed to start STS service", e);
        }

        if (exitCode != 0) {
            throw new SecureTokenServerInstallerException(String.format(
                    "Failed to start STS service [error code: %d]", exitCode),
                    null);
        }
    }

    public void checkSTSHealth() throws Exception {
        String stsEndpoint = null;
        // Load the VKS keystore
        KeyStore vksKeyStore = getVksKeyStore();

        // Initialize TrustManager and SSLFactory
        TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance("PKIX");
        trustMgrFactory.init(vksKeyStore);
        SSLContext sslCnxt = SSLContext.getInstance("SSL");
        sslCnxt.init(null, trustMgrFactory.getTrustManagers(), null);
        SSLSocketFactory sslFactory = sslCnxt.getSocketFactory();
        // validate if all the web applications are deployed successfully.
        long iterations = MAX_TIME_TO_WAIT_MILLIS / WAIT_TIME_PER_ITERATION;
        for (int x = 0; x < iterations; x++) {
            int httpResponseCode = 0;
            if (stsEndpoint == null) {
                stsEndpoint = tryGetStsEndpoint();
            }

            if (stsEndpoint != null) {
                httpResponseCode = tryPollStsHealth(sslFactory, stsEndpoint);
            }

            if (httpResponseCode == 200) {
                System.out.println(String.format("STS is deployed successfully"));
                return;
            }

            Thread.sleep(WAIT_TIME_PER_ITERATION);
        }

        String message = String.format("Error: STS did not come up after %d ms", MAX_TIME_TO_WAIT_MILLIS);
        throw new STSWebappNotDeployedException(message);
    }

    private String tryGetStsEndpoint() {
        String stsHostname = null;
        try {
            // get fqdn of current instance
            VmAfClient afdClient = new VmAfClient("localhost");
            stsHostname = afdClient.getDomainController();
        } catch (Exception e) {
            System.out.println("Failed to get domain controller name from vmafd: " + e.getMessage());
            return null;
        }

        return "https://" + stsHostname + STS_HEALTH_ENDPOINT;
    }

    private int tryPollStsHealth(SSLSocketFactory sslFactory, String stsEndpoint) {
        HttpsURLConnection connection = null;
        int httpResponseCode = -1;

        try {
            URL stsEndpointUrl = new URL(stsEndpoint);
            connection = (HttpsURLConnection) stsEndpointUrl.openConnection();
            connection.setSSLSocketFactory(sslFactory);
            connection.setRequestMethod("GET");
            connection.connect();
            httpResponseCode = connection.getResponseCode();
        } catch (Exception e) {
            String message = String.format("Unable to poll STS health (%s): %s", stsEndpoint, e.getMessage());
            System.out.println(message);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return httpResponseCode;
    }

    /**
     * Get VKS keystore by calling VECS
     */
    public KeyStore getVksKeyStore() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        KeyStore ks = KeyStore.getInstance(VKS_KEYSTORE_INSTANCE);
        ks.load(new VecsLoadStoreParameter(VKS_KEYSTORE_NAME));
        return ks;
    }

    private void installInstAsWinService()
            throws SecureTokenServerInstallerException {
        if (SystemUtils.IS_OS_WINDOWS) {

            createJavaSecurityFile();
            String wrapper_bin = new WinInstallerHelper().getWrapperBinPath();
            String wrapper_exe = InstallerUtils.joinPath(wrapper_bin, "wrapper.exe");
            String wrapper_conf = InstallerUtils.joinPath(InstallerUtils
                        .getInstallerHelper().getTCBase(), "conf", "wrapper.conf");

            // Install STS instance as windows service using Service controller
            String sc_binPath = "\"" + wrapper_exe + " -s " +  wrapper_conf +"\"";
            String command = "sc.exe create VMwareSTS  type= own start= auto error= normal binPath= "+sc_binPath+ " depend= VMwareIdentityMgmtService displayname= \"VMware Secure Token Service\" ";
            System.out.println(command);
            int exitCode = -1;
            try {
                Process p = Runtime.getRuntime().exec(command);
                exitCode = p.waitFor();
            } catch (IOException | InterruptedException e) {
                throw new SecureTokenServerInstallerException(
                        "Failed to install STS instance as Win service", e);
            }

            if (exitCode != 0) {
                throw new SecureTokenServerInstallerException(String.format(
                        "Failed to install STS service [error code: %d]",
                        exitCode), null);
            }

            // update the description
            exitCode = -1;
            String desc = "VMware Single Sign-On STS Service";
            command = "sc.exe description VMwareSTS "+desc ;
            try {
                Process p = Runtime.getRuntime().exec(command);
                exitCode = p.waitFor();
            } catch (IOException | InterruptedException e) {
                throw new SecureTokenServerInstallerException(
                        "Failed to set VMwareSTS service description", e);
            }

            if (exitCode != 0) {
                throw new SecureTokenServerInstallerException(
                        String.format(
                                "Failed to set VMwareSTS service description [error code: %d]",
                                exitCode), null);
            }

            // Set recovery options
            exitCode = -1;
            command = "sc failure VMwareSTS reset= 86400 actions= restart/30000/restart/60000/restart/90000";
            try {
                Process p = Runtime.getRuntime().exec(command);
                exitCode = p.waitFor();
            } catch (IOException | InterruptedException e) {
                throw new SecureTokenServerInstallerException(
                        "Failed to set VMwareSTS service recovery options", e);
            }

            if (exitCode != 0) {
                throw new SecureTokenServerInstallerException(
                        String.format(
                                "Failed to set VMwareSTS service recovery options [error code: %d]",
                                exitCode), null);
            }
        }

    }

    private int runCommand(String command, String logFile,
            String workingDirectory) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null)
            pb.directory(new File(workingDirectory));
        pb.redirectErrorStream(true);

        File log = new File(logFile);
        pb.redirectOutput(Redirect.appendTo(log));

        int exitCode = -1;

        final Process p = pb.start();
        exitCode = p.waitFor();

        return exitCode;
    }

    @Override
    public void upgrade() {
        log.debug("SecureTokenServerInstaller : Upgrade");
        mergeServerXMl();

    }

    @Override
    public void uninstall() {
        // TODO Auto-generated method stub

    }

    @Override
    public void migrate() {

    }

    private void configureSTS() throws STSInstallerException {
        if (SystemUtils.IS_OS_LINUX) {
            String configStsPath = InstallerUtils.getInstallerHelper()
                    .getConfigureStsPath();
            Path configurePath = Paths.get(configStsPath);
            try {
                String configFileName = InstallerUtils.getInstallerHelper()
                        .getConfigureStsFileName();
                InputStream link = getClass().getClassLoader().getResourceAsStream(
                        configFileName);// "configure-sts.sh"));

                if (link != null && configurePath != null) {
                    try {
                        Files.copy(link, configurePath);
                    } catch (IOException e) {
                        log.error(e.toString());
                        throw new STSInstallerException("Failed to extract "
                                + configFileName, e);
                    }

                    InstallerUtils.getInstallerHelper().setPermissions(
                            configurePath);

                    ProcessBuilder pb = new ProcessBuilder(configurePath.toString());
                    pb.redirectErrorStream(true);

                    File log = new File(configStsPath + ".log");
                    pb.redirectOutput(Redirect.appendTo(log));
                    final Process p = pb.start();

                    int exitCode = p.waitFor();

                    if (exitCode != 0) {
                        throw new STSInstallerException(String.format(
                                "Failed to run %s, errorcode %s", configStsPath,
                                exitCode), null);
                    }
                }
            } catch (InterruptedException | IOException e) {
                throw new STSInstallerException("Failed to run " + configStsPath, e);
            } finally {
                try {
                    Files.deleteIfExists(configurePath);
                } catch (IOException e) {
                    log.debug("Failed to delete %s", configurePath);
                }
            }
        }
    }
    private void mergeServerXMl() {

        getServerAttributes();
        setServerAttributes();

    }

    private void getServerAttributes() {

        File serverXmlFile = new File(InstallerUtils.joinPath(params.getBackupDir() , "server.xml"));
        try {
            DocumentBuilder builder = xmlParserFactory.newDocumentBuilder();
            Document doc = builder.parse(serverXmlFile);
            NodeList connectorList = doc.getElementsByTagName(CONNECTOR);
            NodeList sslHostConfigList = doc.getElementsByTagName(SSL_HOST_CONFIG);
            NodeList cerficateList = doc.getElementsByTagName(CERTIFICATE_ATTR);
            for (int i = 0 ; i < connectorList.getLength(); i ++) {

                Node connectorNode = connectorList.item(i);
                Node sslHostConfigNode = sslHostConfigList.item(i);
                Node certificateNode = cerficateList.item(i);
                Element connectorElement = (Element) connectorNode;
                Element sslHostConfigElement = (Element) sslHostConfigNode;
                Element certificateElement = (Element) certificateNode;
                boolean isSSLEnabled = connectorElement.hasAttribute(SSL_ENABLED_ATTR);
                if (isSSLEnabled) {
                    sslEnabledProtocols = sslHostConfigElement.getAttribute(SSL_ENABLED_PROTOCOL_ATTR);
                    log.debug("SecureTokenServerInstaller : getServerAttributes sslEnabledProtocols: %s", sslEnabledProtocols);
                    storename  = connectorElement.getAttribute(STORE_ATTR);
                    log.debug("SecureTokenServerInstaller : getServerAttributes storename: %s", storename);
                    sslImplementationName = connectorElement.getAttribute(SSL_IMPLEMENTATION_NAME_ATTR);
                    log.debug("SecureTokenServerInstaller : getServerAttributes sslImplementationName: %s", sslImplementationName);
                    keyAlias  = certificateElement.getAttribute(KEYALIAS_ATTR);
                    log.debug("SecureTokenServerInstaller : getServerAttributes keyAlias:" + keyAlias);
                    keyStoreType  = certificateElement.getAttribute(KEYSTORETYPE_ATTR);
                    log.debug("SecureTokenServerInstaller : getServerAttributes keyStoreType:" + keyStoreType);

                } else {
                    continue;
                }

            }
            log.debug("SecureTokenServerInstaller : getServerAttributes - Completed");
        } catch (Exception ex) {
            log.error("SecureTokenServerInstaller : getServerAttributes - failed");
        }

    }

    private void setServerAttributes() {

	try {
        String filePath = InstallerUtils.joinPath( InstallerUtils.getInstallerHelper().getTCBase(),
                            "conf","server.xml");
            DocumentBuilder builder = xmlParserFactory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            NodeList connectorList = doc.getElementsByTagName(CONNECTOR);
            NodeList sslHostConfigList = doc.getElementsByTagName(SSL_HOST_CONFIG);
            NodeList cerficateList = doc.getElementsByTagName(CERTIFICATE_ATTR);
            for (int i = 0; i < connectorList.getLength(); i++) {

                Node connectorNode = connectorList.item(i);
                Node sslHostConfigNode = sslHostConfigList.item(i);
                Node certificateNode = cerficateList.item(i);
                Element connectorElement = (Element) connectorNode;
                Element sslHostConfigElement = (Element) sslHostConfigNode;
                Element certificateElement = (Element) certificateNode;
                boolean isSSLEnabled = connectorElement.hasAttribute(SSL_ENABLED_ATTR);
                if (isSSLEnabled) {
                    sslHostConfigElement.setAttribute(SSL_ENABLED_PROTOCOL_ATTR, sslEnabledProtocols);
                    connectorElement.setAttribute(STORE_ATTR,storename);
                    connectorElement.setAttribute(SSL_IMPLEMENTATION_NAME_ATTR, sslImplementationName);
                    certificateElement.setAttribute(KEYALIAS_ATTR, keyAlias);
                    certificateElement.setAttribute(KEYSTORETYPE_ATTR, keyStoreType);

                } else {
                    continue;
                }
            }

            TransformerFactory transformerFactory = SecureTransformerFactory.newTransformerFactory();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult (new File(filePath));
            transformer.transform(source, result);
            log.debug("SecureTokenServerInstaller : setServerAttributes - Completed");
        } catch (Exception ex) {
           log.error("SecureTokenServerInstaller : setServerAttributes - failed");
        }

    }
    private void createJavaSecurityFile() {
        try {
        File  securityFile = new  File(InstallerUtils.joinPath(System.getenv("VMWARE_CFG_DIR"), "java","vmware-override-java.security"));
        File vmIdentitySecurityFile = new File (InstallerUtils.joinPath(InstallerUtils
                        .getInstallerHelper().getTCBase(), "conf","vmware-identity-override-java.security"));
        Files.copy(securityFile.toPath(), vmIdentitySecurityFile.toPath());
        } catch (Exception ex ) {
            System.out.println("Failedin to create VMIdentity java security File");
        }

    }
    @Override
    public PlatformInstallComponent getComponentInfo() {
        return new PlatformInstallComponent(ID, Name, Description);
    }
}
