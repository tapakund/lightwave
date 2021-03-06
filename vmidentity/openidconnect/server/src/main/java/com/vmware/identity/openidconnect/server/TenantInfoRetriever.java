/*
 *  Copyright (c) 2012-2015 VMware, Inc.  All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, without
 *  warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

package com.vmware.identity.openidconnect.server;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.vmware.identity.idm.AuthnPolicy;
import com.vmware.identity.idm.IDPConfig;
import com.vmware.identity.idm.NoSuchTenantException;
import com.vmware.identity.idm.Tenant;
import com.vmware.identity.idm.client.CasIdmClient;
import com.vmware.identity.openidconnect.common.ErrorObject;
import com.vmware.identity.openidconnect.common.Issuer;

/**
 * @author Yehia Zayour
 */
public class TenantInfoRetriever {
    private final CasIdmClient idmClient;

    public TenantInfoRetriever(CasIdmClient idmClient) {
        Validate.notNull(idmClient);
        this.idmClient = idmClient;
    }

    public TenantInfo retrieveTenantInfo(String tenantName) throws ServerException {
        Validate.notEmpty(tenantName, "tenantName");

        Tenant tenantObject;
        try {
            tenantObject = this.idmClient.getTenant(tenantName);
        } catch (NoSuchTenantException e) {
            throw new ServerException(ErrorObject.invalidRequest("non-existent tenant"), e);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving tenant"), e);
        }

        if (tenantObject == null) {
            throw new ServerException(ErrorObject.invalidRequest("non-existent tenant"));
        }
        String tenant = tenantObject.getName(); // use tenant name as it appears in directory

        RSAPrivateKey privateKey;
        try {
            privateKey = (RSAPrivateKey) this.idmClient.getTenantPrivateKey(tenant);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving tenant private key"), e);
        }

        if (privateKey == null) {
            throw new ServerException(ErrorObject.serverError("tenant signer key is not set"));
        }

        List<Certificate> certChain;
        try {
            certChain = this.idmClient.getTenantCertificate(tenant);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving tenant cert"), e);
        }

        if ( ( certChain == null ) || (certChain.size() == 0) ) {
            throw new ServerException(ErrorObject.serverError("tenant signing certs are not set"));
        }

        X509Certificate certificate = (X509Certificate) certChain.get(0);
        if ( certificate == null ) {
            throw new ServerException(ErrorObject.serverError("tenant signing cert is not set"));
        }

        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        if ( publicKey == null ) {
            throw new ServerException(ErrorObject.serverError("tenant signing cert does not have public key"));
        }

        AuthnPolicy authnPolicy;
        String issuer;
        String secondaryIssuer;
        String brandName;
        String logonBannerTitle;
        String logonBannerContent;
        boolean logonBannerEnableCheckbox;
        long idTokenBearerLifetimeMs;
        long idTokenHokLifetimeMs;
        long accessTokenBearerLifetimeMs;
        long accessTokenHokLifetimeMs;
        long refreshTokenBearerLifetimeMs;
        long refreshTokenHokLifetimeMs;
        long clockToleranceMs;
        try {
            authnPolicy                  = this.idmClient.getAuthnPolicy(tenant);
            issuer                       = this.idmClient.getOIDCEntityID(tenant);
            secondaryIssuer              = this.idmClient.getSecondaryOIDCEntity(tenant);
            brandName                    = this.idmClient.getBrandName(tenant);
            logonBannerTitle             = this.idmClient.getLogonBannerTitle(tenant);
            logonBannerContent           = this.idmClient.getLogonBannerContent(tenant);
            logonBannerEnableCheckbox    = this.idmClient.getLogonBannerCheckboxFlag(tenant);
            idTokenBearerLifetimeMs      = this.idmClient.getMaximumBearerTokenLifetime(tenant);
            idTokenHokLifetimeMs         = this.idmClient.getMaximumHoKTokenLifetime(tenant);
            accessTokenBearerLifetimeMs  = this.idmClient.getMaximumBearerTokenLifetime(tenant);
            accessTokenHokLifetimeMs     = this.idmClient.getMaximumHoKTokenLifetime(tenant);
            refreshTokenBearerLifetimeMs = this.idmClient.getMaximumBearerRefreshTokenLifetime(tenant);
            refreshTokenHokLifetimeMs    = this.idmClient.getMaximumHoKRefreshTokenLifetime(tenant);
            clockToleranceMs             = this.idmClient.getClockTolerance(tenant);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving tenant properties"), e);
        }

        String securIdLoginGuide = (authnPolicy.get_rsaAgentConfig() != null) ? authnPolicy.get_rsaAgentConfig().get_loginGuide() : null;
        TenantInfo.AuthnPolicy tenantAuthnPolicy = new TenantInfo.AuthnPolicy(
                authnPolicy.IsPasswordAuthEnabled(),
                authnPolicy.IsTLSClientCertAuthnEnabled(),
                authnPolicy.IsWindowsAuthEnabled(),
                authnPolicy.IsRsaSecureIDAuthnEnabled(),
                securIdLoginGuide);

        return new TenantInfo.Builder(tenant).
                privateKey(privateKey).
                publicKey(publicKey).
                certificate(certificate).
                authnPolicy(tenantAuthnPolicy).
                issuer(new Issuer(issuer, secondaryIssuer)).
                brandName(brandName).
                logonBannerTitle(logonBannerTitle).
                logonBannerContent(logonBannerContent).
                logonBannerEnableCheckbox(logonBannerEnableCheckbox).
                idTokenBearerLifetimeMs(idTokenBearerLifetimeMs).
                idTokenHokLifetimeMs(idTokenHokLifetimeMs).
                accessTokenBearerLifetimeMs(accessTokenBearerLifetimeMs).
                accessTokenHokLifetimeMs(accessTokenHokLifetimeMs).
                refreshTokenBearerLifetimeMs(refreshTokenBearerLifetimeMs).
                refreshTokenHokLifetimeMs(refreshTokenHokLifetimeMs).
                clockToleranceMs(clockToleranceMs).build();
    }

    public String getDefaultTenantName() throws ServerException {
        String tenant;

        try {
            tenant = this.idmClient.getDefaultTenant();
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving default tenant name"), e);
        }

        if (tenant == null) {
            throw new ServerException(ErrorObject.serverError("default tenant is not configured"));
        }

        return tenant;
    }

    public String getTenantIssuer(String tenantName) throws ServerException {
        try {
            return this.idmClient.getIssuer(tenantName);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving issuer name"), e);
        }
    }

    public String getLocalOIDCEntityID(String tenantName) throws ServerException {
        try {
            return this.idmClient.getOIDCEntityID(tenantName);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving local oidc entity id"), e);
        }
    }

    public String getSystemTenantName() throws ServerException {
        try {
            return this.idmClient.getSystemTenant();
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError("idm error while retrieving system tenant name"), e);
        }
    }

    public IDPConfig getExternalIDPConfig(String tenant, String entityId) throws ServerException {
        try {
            return idmClient.getExternalIdpConfigForTenant(tenant, entityId);
        } catch (Exception e) {
            throw new ServerException(ErrorObject.serverError(
                String.format("idm error while retrieving external idp config for tenant '%s'", tenant)), e);
        }
    }
}