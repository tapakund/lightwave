/*
 * Copyright © 2018 VMware, Inc.  All Rights Reserved.
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

#ifdef __cplusplus
extern "C" {
#endif

// parse.c

DWORD
LwCAPolicyParseCfgPolicies(
    PLWCA_JSON_OBJECT           pJson,
    PLWCA_POLICIES              *ppPolicies
    );

// validate.c

DWORD
LwCAPolicyValidateSNPolicy(
    PLWCA_POLICY_CFG_OBJ_ARRAY  pSNsAllowed,
    PLWCA_REQ_CONTEXT           pReqContext,
    X509_REQ                    *pRequest
    );

DWORD
LwCAPolicyValidateSANPolicy(
    PLWCA_POLICY_CFG_OBJ_ARRAY  pSANsAllowed,
    BOOLEAN                     bMultiSANEnabled,
    PLWCA_REQ_CONTEXT           pReqContext,
    X509_REQ                    *pRequest
    );

DWORD
LwCAPolicyValidateKeyUsagePolicy(
    DWORD                       dwAllowedKeys,
    X509_REQ                    *pRequest
    );

DWORD
LwCAPolicyValidateCertDurationPolicy(
    DWORD                       dwAllowedDuration,
    PLWCA_CERT_VALIDITY         pValidity
    );

#ifdef __cplusplus
}
#endif
