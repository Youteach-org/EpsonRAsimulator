# InitializeObserve — prepared, NOT EXECUTED

Implementation: 986d7804 (Windows CI105: readiness36/36, research77/77; Android CI530 SUCCESS). LoadOnly passed once; no subsequent native stage has run.

## Exact proposed boundary
One x86 worker loads the installed assembly, constructs Spel, assigns ServerInstance=10, calls Initialize(), then Dispose(). The parent collects process/TCP samples and stage markers. Initialize can start an Epson RC+ server; first activating access may have implicit effects. No Inventory, GetCurrentConnectionInfo, Connect, robot/project/motion/task/I-O/SPEL calls.

Exact target policy remains C4 Sample, but this stage does not select or verify a connection. Never substitute connection number2 or another target.

ServerInstance10 is explicitly proposed, not certified unused. Read-only check at 2026-09-29T19:26:01.3054643Z found zero erc70/erc70PServer processes. Recheck before execution; user approval must include use of instance10 and confirmation it is not reserved by another application. Preserve existing AutoConnectOFF/no physical controller/RC+closed confirmations unless changed evidence.

## Prepared request
Local outputs/compiled-reviewed/initialize-observe.proposed.json:

```json
{"stage":"InitializeObserve","installRoot":"C:\\EpsonRC70","target":"C4 Sample","serverInstance":10,"approved":true}
```

SHA256: 410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B.
The approved=true field is a proposed execution input, not evidence of user authorization.

## Exact command

```powershell
$researchRunRoot = 'C:\Users\BATMAN\Documents\Codex\2026-09-19\files-pasted-by-the-user-contin\work\EpsonRAsimulator-phase6d\outputs\compiled-reviewed'
& "$researchRunRoot\x64\EpsonRa.Bridge.Research.Supervisor.exe" --worker "$researchRunRoot\x86\EpsonRa.Bridge.Research.Worker.exe" --request "$researchRunRoot\initialize-observe.proposed.json" --timeout-seconds 30
```

Binary hashes rechecked unchanged:
- x86 worker: 0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4
- x64 supervisor: FAE1DDEBB4725FEBDCB940A3DC40A9F95DB6A29646B251B79F1018D4C650337A
- research DLL in both directories: ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF

## Result handling
Recheck hashes immediately before approved execution. Run once, deadline30seconds, no architecture fallback/retry. Terminate only the owned worker on timeout; never kill shared Epson server processes. Persist normalized result, timings, stderr size and post-run process count. Failed/unknown cleanup, timeout, observation gaps or leftover server state stop progression; no automatic Inventory. Polling cannot prove absence of brief network/USB activity or an implicit connection.

Separate exact approval is required by docs/superpowers/plans/2026-09-28-compiled-native-research-host.md: “Native stage approvals are separate: LoadOnly, InitializeObserve, Inventory, Connect.”
