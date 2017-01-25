/*
 * Copyright © 2012-2016 VMware, Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the “License”); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS, without
 * warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

#ifndef _PROTOTYPES_H_
#define _PROTOTYPES_H_

// OIDC_SERVER_METADATA

SSOERROR
OidcServerMetadataAcquire(
    POIDC_SERVER_METADATA* pp,
    PCSTRING pszServer,
    int portNumber,
    PCSTRING pszTenant);

void
OidcServerMetadataDelete(
    POIDC_SERVER_METADATA p);

PCSTRING
OidcServerMetadataGetTokenEndpointUrl(
    PCOIDC_SERVER_METADATA p);

PCSTRING
OidcServerMetadataGetSigningCertificatePEM(
    PCOIDC_SERVER_METADATA p);

// OIDC_ID_TOKEN

SSOERROR
OidcIDTokenParse(
    POIDC_ID_TOKEN* pp,
    PCSTRING psz);

// OIDC_ACCESS_TOKEN

SSOERROR
OidcAccessTokenParse(
    POIDC_ACCESS_TOKEN* pp,
    PCSTRING psz);

// OIDC_TOKEN_SUCCESS_RESPONSE

SSOERROR
OidcTokenSuccessResponseParse(
    POIDC_TOKEN_SUCCESS_RESPONSE* pp,
    PCSTRING pszJsonResponse,
    PCSTRING pszSigningCertificatePEM,
    SSO_LONG clockToleranceInSeconds);

// OIDC_ERROR_RESPONSE

SSOERROR
OidcErrorResponseParse(
    POIDC_ERROR_RESPONSE* pp,
    PCSTRING pszJsonResponse);

SSOERROR
OidcErrorResponseGetSSOErrorCode(
    PCOIDC_ERROR_RESPONSE p);

#endif
