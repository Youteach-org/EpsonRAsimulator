# Read-only startup diagnosis — 2026-10-01

No Windows Application events1000/1002/1026 matched the fifth attempt interval17:36:50Z–17:39:00Z. Application log is readable. Prior capture incorrectly classified NoMatchingEventsFound as unavailable; original evidence remains unchanged. This narrow negative result does not prove absence of all errors.

Registry Install.Version=7.5.3, Misc.AppPath points to installed root. Misc.OS contains legacy Windows7; this is a stored application value, not actual OS identity or proven cause. Version subkey has no nested settings. No configuration was changed.

Read-only x86 ReflectionOnlyLoadFrom metadata inspection resolved direct managed references of erc70.exe, erc70main.dll and RCAPINet.dll. Requested versions matched except CLR reflection unification of mscorlib2 to4; not proof of actual runtime binding behavior. No Epson method/constructor was invoked by this inspection. Transitive/dynamic/COM dependencies remain unverified. OutputSHA256 D62A5203290AFA32EA0FDFEFD009E2BAA53E43F51AC069CC03E2C5735D9632FD.

Native static imports show x86 erc70PIF.dll in SysWOW64; WBCommon70.dll also exists there, not under Epson exe. VCRUNTIME140 and mscoree x86 files exist. Initial app-directory-only absence is NOT a missing-dependency finding. API-set import names are not validated by checking for same-named files. No repair/reinstall indicated by this limited check.

Local call metadata inspection of Initialize found parameterless Process.WaitForInputIdle, wait handles and subsequent server-readiness/IPC operations. This is static potential flow, not an observed stack or proof of blockage location. An existing-process path can call Process.Kill; clean baseline remains required. Proprietary instruction/call metadata remains local and is not published.

Next discriminating observation: a bounded one-shot input-idle sampler during an otherwise unchanged30s attempt, without calling any additional Epson API. True samples would disfavor an ongoing input-idle wait; false/error cannot identify root cause. Preserve all prior evidence and no automatic process cleanup.

