/*
 * Copyright © 2097 VMware, Inc.  All Rights Reserved.
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

#include "includes.h"


DWORD
InitializeLockoutSetup(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;
    DWORD   dwCnt = 0;
    PSTR ppszAttrValue[] = { NULL, NULL };

    struct _LockoutParam
    {
        PSTR pszAttrName;
        PSTR pszValue;
    }
    LockoutParam[] =
    {
        { ATTR_PASS_AUTO_UNLOCK_SEC,  "2" },
        { ATTR_PASS_FAIL_ATTEMPT_SEC, "2" },
        { ATTR_PASS_MAX_FAIL_ATTEMPT, "2" },
    };

    for (dwCnt=0; dwCnt < sizeof(LockoutParam)/sizeof(LockoutParam[0]); dwCnt++)
    {
        ppszAttrValue[0] = LockoutParam[dwCnt].pszValue;
        dwError = VmDirTestReplaceAttributeValues(
            pPolicyContext->pTestState->pLd,
            pPolicyContext->pszPolicyDN,
            LockoutParam[dwCnt].pszAttrName,
            (PCSTR*)ppszAttrValue);
        BAIL_ON_VMDIR_ERROR(dwError);
    }

error:
    return dwError;
}

VOID
TestTriggerLoginLockout(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext,
    PCSTR   pszHost,
    PCSTR   pszDomain,
    PCSTR   pszUser
    )
{
    DWORD   dwError = 0;
    DWORD   dwCnt = 0;
    PVMDIR_TEST_STATE pState = pPolicyContext->pTestState;
    PCSTR   pszJunkPassword = "junk";
    LDAP*   pLd = NULL;

    for (dwCnt=0; dwCnt < 2; dwCnt++)
    {
        dwError = VmDirTestConnectionUser(pszHost, pszDomain, pszUser, pszJunkPassword, &pLd);
        TestAssertEquals(dwError, 9234); // invalid credentials
    }
}

DWORD
TestFailedLoginLockout(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext,
    PCSTR   pszHost,
    PCSTR   pszDomain,
    PCSTR   pszUser,
    PCSTR   pszPassword,
    LDAP**  ppLd
    )
{
    DWORD   dwError = 0;

    TestTriggerLoginLockout(pPolicyContext, pszHost, pszDomain, pszUser);

    dwError = VmDirTestConnectionUser(pszHost, pszDomain, pszUser, pszPassword, ppLd);

    return dwError;
}


DWORD
TestAdminFailedLoginLockout(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;
    PVMDIR_TEST_STATE pState = pPolicyContext->pTestState;
    LDAP*   pLd = NULL;

    dwError = TestFailedLoginLockout(
        pPolicyContext,
        pState->pszServerName,
        pState->pszDomain,
        pState->pszUserName,
        pState->pszPassword,
        &pLd);
    TestAssertEquals(dwError, 0);  // admin NOT subject to lockout

    ldap_unbind_ext_s(pLd, NULL, NULL);

    return dwError;
}

DWORD
TestUserFailedLoginLockout(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;
    PVMDIR_TEST_STATE pState = pPolicyContext->pTestState;
    LDAP*   pLd = NULL;

    dwError = TestFailedLoginLockout(
        pPolicyContext,
        pState->pszServerName,
        pState->pszDomain,
        pPolicyContext->pszTestUserCN,
        pPolicyContext->pszTestUserPassword,
        &pLd);
    TestAssertEquals(dwError, 9234);  // user lockout

    return 0;
}

DWORD
TestUserFailedLoginAttemptInterval(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;
    PVMDIR_TEST_STATE pState = pPolicyContext->pTestState;
    LDAP*   pLd = NULL;

    dwError = VmDirTestConnectionUser(
       pState->pszServerName,
       pState->pszDomain,
       pPolicyContext->pszTestUserCN,
       "Junk",
       &pLd);
    TestAssertEquals(dwError, 9234);

    // sleep for 3 secs > ATTR_PASS_FAIL_ATTEMPT_SEC
    VmDirSleep(3000);

    dwError = VmDirTestConnectionUser(
       pState->pszServerName,
       pState->pszDomain,
       pPolicyContext->pszTestUserCN,
       "Junk",
       &pLd);
    TestAssertEquals(dwError, 9234);

    // since two login failed with > ATTR_PASS_FAIL_ATTEMPT_SEC apart
    // account should still active
    dwError = VmDirTestConnectionUser(
       pState->pszServerName,
       pState->pszDomain,
       pPolicyContext->pszTestUserCN,
       pPolicyContext->pszTestUserPassword,
       &pLd);
    TestAssertEquals(dwError, 0);

    ldap_unbind_ext_s(pLd, NULL, NULL);

    return dwError;
}

DWORD
TestUserFailedLoginAutoUnlock(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;
    PVMDIR_TEST_STATE pState = pPolicyContext->pTestState;
    LDAP*   pLd = NULL;

    // sleep for 3 secs > ATTR_PASS_AUTO_UNLOCK_SEC
    VmDirSleep(3000);

    dwError = VmDirTestConnectionUser(
       pState->pszServerName,
       pState->pszDomain,
       pPolicyContext->pszTestUserCN,
       pPolicyContext->pszTestUserPassword,
       &pLd);
    TestAssertEquals(dwError, 0);   // auto unlock with correct login

    ldap_unbind_ext_s(pLd, NULL, NULL);

    return dwError;
}

DWORD
TestAdminLockoutUser(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;

    // TODO
    return dwError;
}

DWORD
TestAdminLockoutAdmin(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;

    // TODO
    return dwError;
}

// T - admin set user useraccountcontrol to (disable/2)
//      user login should fail

// T - add user to cn=administrators,cn=builtin,XXX group
//      user disable flag set but can still login
//      should ?

// T - admin set self useraccountcontrol to (disable/2)
//      admin disable flag set but can still login
//      should pass

DWORD
TestLockout(
    PVMDIR_PPOLICY_TEST_CONTEXT   pPolicyContext
    )
{
    DWORD   dwError = 0;

    dwError = InitializeLockoutSetup(pPolicyContext);
    BAIL_ON_VMDIR_ERROR(dwError);

    dwError = TestAdminFailedLoginLockout(pPolicyContext);
    BAIL_ON_VMDIR_ERROR(dwError);

    dwError = TestUserFailedLoginLockout(pPolicyContext);
    BAIL_ON_VMDIR_ERROR(dwError);

    // by now, user should be in lockout state
    dwError = TestUserFailedLoginAutoUnlock(pPolicyContext);
    BAIL_ON_VMDIR_ERROR(dwError);

    // by now, user should be in active state
    dwError = TestUserFailedLoginAttemptInterval(pPolicyContext);
    BAIL_ON_VMDIR_ERROR(dwError);

cleanup:
    printf("%s %s (%d)\n", __FUNCTION__, dwError ? "failed" : "succeeded", dwError);
    return dwError;

error:
    goto cleanup;
}
