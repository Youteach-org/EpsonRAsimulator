using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Threading;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Supervisor;
class DesktopObservationOnly {
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool OpenProcessToken(IntPtr process,uint access,out IntPtr token);
 [DllImport("advapi32.dll")] static extern bool IsTokenRestricted(IntPtr token);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr token);
 static void Verify(string path,string hash){using(var sha=SHA256.Create())using(var f=File.OpenRead(path)){if(BitConverter.ToString(sha.ComputeHash(f)).Replace("-","")!=hash)throw new InvalidOperationException("Observer artifact mismatch");}}
 static bool? Restricted(){IntPtr t;if(!OpenProcessToken(Process.GetCurrentProcess().Handle,8,out t))return null;try{return IsTokenRestricted(t);}finally{CloseHandle(t);}}
 static int Main(){
  try{
   string root=AppDomain.CurrentDomain.BaseDirectory;
   Verify(Path.Combine(root,"EpsonRa.Bridge.Research.Supervisor.exe"),"763458C8057D7312B178F2AD9E577FEFBCCCAA9DC5259EEFF5A68B62F3C86661");
   Verify(Path.Combine(root,"EpsonRa.Bridge.Research.dll"),"ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF");
   var timer=Stopwatch.StartNew();ObservationSnapshot before,after;
   using(var monitor=new SystemObservationMonitorFactory().Create(@"C:\EpsonRC70")){
    before=monitor.Before;
    monitor.WorkerStarted(Process.GetCurrentProcess().Id);
    for(int i=0;i<3;i++){Thread.Sleep(250);monitor.Poll();}
    monitor.Dispose();after=monitor.After;
   }
   var report=new{capturedUtc=DateTime.UtcNow.ToString("o"),purpose="OS observer only; self PID stands in for worker; not v8 evidence",restrictedToken=Restricted(),epsonApiLoaded=false,epsonStarted=false,elapsedMs=timer.ElapsedMilliseconds,before=before,after=after,comparison=ObservationEvaluator.Compare(before,after)};
   string directory=Path.GetFullPath(Path.Combine(root,"..","..","..","outputs"));
   string path=Path.Combine(directory,"observer-only-"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff")+".json");
   using(var f=new FileStream(path,FileMode.CreateNew,FileAccess.Write))using(var w=new StreamWriter(f)){w.Write(new JavaScriptSerializer().Serialize(report));}
   Console.WriteLine(path);return 0;
  }catch(Exception e){Console.WriteLine("OBSERVER_CAPTURE_ERROR "+e.GetType().Name+": "+e.Message);return 2;}
 }
}

