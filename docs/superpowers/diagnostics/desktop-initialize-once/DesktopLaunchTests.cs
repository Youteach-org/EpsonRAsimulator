using System;
using System.Diagnostics;
using System.IO;
class DesktopLaunchTests {
 static int Main(){try{
  string root=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"desktop-launch-tests-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(root);
  string fixture=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"DesktopLaunchFixture.exe");
  string marker=Path.Combine(root,"normal.marker"),receipt=Path.Combine(root,"normal.receipt.json");
  int code=DesktopLaunchCore.Run(fixture,"\""+marker+"\" 0 7 \""+receipt+"\"",receipt,Path.Combine(root,"normal.result"),3000);
  if(code!=7||!File.Exists(marker)||!File.ReadAllText(receipt).Contains("SUPERVISOR_EXIT_OBSERVED"))throw new Exception("normal exit/receipt not captured");
  string before=File.ReadAllText(receipt);bool rejected=false;
  try{DesktopLaunchCore.Run(fixture,"\""+Path.Combine(root,"replay.marker")+"\" 0 0",receipt,Path.Combine(root,"normal.result"),3000);}catch(IOException){rejected=true;}
  if(!rejected||File.Exists(Path.Combine(root,"replay.marker"))||File.ReadAllText(receipt)!=before)throw new Exception("replay not rejected unchanged");
  string timeoutMarker=Path.Combine(root,"timeout.marker"),timeoutReceipt=Path.Combine(root,"timeout.receipt.json");var watch=Stopwatch.StartNew();
  code=DesktopLaunchCore.Run(fixture,"\""+timeoutMarker+"\" 60000 0",timeoutReceipt,Path.Combine(root,"timeout.result"),700);
  if(code!=124||watch.ElapsedMilliseconds>5000||!File.ReadAllText(timeoutReceipt).Contains("INCONCLUSIVE_OUTER_TIMEOUT"))throw new Exception("outer bound not preserved");
  using(var child=Process.GetProcessById(int.Parse(File.ReadAllText(timeoutMarker)))){if(child.HasExited)throw new Exception("launcher killed timed-out child");child.Kill();child.WaitForExit(3000);}
  Console.WriteLine("PASS normal exit, receipt, replay preservation, timeout bound, no automatic child kill");return 0;
 }catch(Exception e){Console.WriteLine("FAIL "+e.GetType().Name+": "+e.Message);return 1;}}
}
