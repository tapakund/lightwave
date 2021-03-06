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

#ifndef _WIN32

#include <config.h>
#include <vmafdsys.h>

#include <lwrpcrt/lwrpcrt.h>
#include <dce/rpc.h>
#include <gssapi/gssapi.h>
#include <gssapi/gssapi_ext.h>

#include <vmafd.h>
#include <vmafddefines.h>

#include <vmafdcommon.h>
#include <vmafdclient.h>
#include <vmafderrorcode.h>
#include <vmsuperlogging.h>
#include <vecsclient.h>
#include <cdcclient.h>
#include <vecslocalclient.h>
#include <vecs_error.h>
#include <vmcommonerror.h>

#include <ssotypes.h>
#include <ssocommon.h>
#include <ssoerrors.h>
#include <oidc_types.h>
#include <oidc.h>

#include "defines.h"
#include "vmafd_h.h"
#include "structs.h"
#include "prototypes.h"
#include "vmafdsuperlog_h.h"

#else //_ WIN32

#pragma once

#include "targetver.h"
#define WIN32_LEAN_AND_MEAN // Exclude rarely-used stuff from Windows headers

#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <ws2tcpip.h>
#include <pthread.h>
#include <dce/rpc.h>
#include <gssapi/gssapi.h>
#include <gssapi/gssapi_ext.h>
#include "banned.h" // windows banned APIs
#include <openssl/x509.h>
#include <vmafd.h>
#include <vmafddefines.h>

#include <vmafdcommon.h>
#include <vmafdclient.h>
#include <vmafderrorcode.h>
#include <vmsuperlogging.h>
#include <vecsclient.h>
#include <cdcclient.h>
#include <vecslocalclient.h>
#include <vecs_error.h>
#include <vmcommonerror.h>

#include <ssotypes.h>
#include <ssocommon.h>
#include <ssoerrors.h>
#include <oidc_types.h>
#include <oidc.h>

#include "defines.h"
#include "vmafd_h.h"
#include "structs.h"
#include "prototypes.h"
#include "vmafdsuperlog_h.h"

#endif // #ifndef _WIN32
