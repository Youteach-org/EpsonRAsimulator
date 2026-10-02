using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;
public static class DesktopLaunchCore {
 static void Save(FileStream stream,Dictionary<string,object> state){byte[] bytes=Encoding.UTF8.GetBytes(new JavaScriptSerializer().Serialize(state));stream.Position=0;stream.SetLength(0);stream.Write(bytes,0,bytes.Length);stream.Flush(true);}
 public static int Run(string executable,string arguments,string receipt,string result,int waitMs){
  if(waitMs<1)throw new ArgumentOutOfRangeException("waitMs");
  if(File.Exists(result)||File.Exists(result+".events.jsonl"))throw new IOException("Existing result evidence; no replay.");
  using(var stream=new FileStream(receipt,FileMode.CreateNew,FileAccess.Write,FileShare.Read)){
   var state=new Dictionary<string,object>{{"startedUtc",DateTime.UtcNow.ToString("o")},{"status","PREPARED_TO_START"},{"supervisorPid",null},{"supervisorExitCode",null},{"cleanup","UNKNOWN"}};
   Process process=null;
   try{
    Save(stream,state);
    process=Process.Start(new ProcessStartInfo(executable,arguments){UseShellExecute=true,WindowStyle=ProcessWindowStyle.Hidden});
    state["supervisorPid"]=process.Id;state["status"]="SUPERVISOR_STARTED";Save(stream,state);
    bool exited=process.WaitForExit(waitMs);
    state["status"]=exited?"SUPERVISOR_EXIT_OBSERVED":"INCONCLUSIVE_OUTER_TIMEOUT";
    if(exited)state["supervisorExitCode"]=process.ExitCode;
    return exited?process.ExitCode:124;
   }catch(Exception e){state["status"]="INCONCLUSIVE_LAUNCH_OR_CAPTURE_ERROR";state["errorType"]=e.GetType().Name;throw;}
   finally{state["resultExists"]=File.Exists(result);state["eventsExist"]=File.Exists(result+".events.jsonl");try{Save(stream,state);}finally{if(process!=null)process.Dispose();}}
  }
 }
}
