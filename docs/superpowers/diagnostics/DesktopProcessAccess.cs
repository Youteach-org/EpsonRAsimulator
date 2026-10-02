using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Web.Script.Serialization;
class DesktopProcessAccess {
 [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,int id);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool QueryFullProcessImageName(IntPtr process,uint flags,StringBuilder path,ref int length);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool OpenProcessToken(IntPtr process,uint access,out IntPtr token);
 [DllImport("advapi32.dll")] static extern bool IsTokenRestricted(IntPtr token);
 static void Count(Dictionary<string,int> values,string key){int count;values.TryGetValue(key,out count);values[key]=count+1;}
 static bool? Restricted(){IntPtr t;if(!OpenProcessToken(Process.GetCurrentProcess().Handle,8,out t))return null;try{return IsTokenRestricted(t);}finally{CloseHandle(t);}}
 static int Main(){try{
  var module=new Dictionary<string,int>();var limited=new Dictionary<string,int>();var unresolved=new Dictionary<string,int>();int total=0,recovered=0,disagreements=0;
  foreach(var p in Process.GetProcesses())using(p){
   int pid;try{pid=p.Id;}catch{continue;}if(pid<=0||pid==4)continue;total++;
   string original=null;try{original=p.MainModule==null?null:p.MainModule.FileName;Count(module,string.IsNullOrEmpty(original)?"Empty":"Success");}catch(Win32Exception e){Count(module,"Win32:"+e.NativeErrorCode);}catch(Exception e){Count(module,e.GetType().Name);}
   bool queried=false;IntPtr handle=OpenProcess(0x1000,false,pid);
   if(handle==IntPtr.Zero)Count(limited,"Open:"+Marshal.GetLastWin32Error());
   else try{
    var path=new StringBuilder(32768);int size=path.Capacity;
    if(QueryFullProcessImageName(handle,0,path,ref size)&&size>0){queried=true;Count(limited,"Success");if(string.IsNullOrEmpty(original))recovered++;else if(!string.Equals(original,path.ToString(),StringComparison.OrdinalIgnoreCase))disagreements++;}
    else Count(limited,"Query:"+Marshal.GetLastWin32Error());
   }finally{CloseHandle(handle);}
   if(!queried&&string.IsNullOrEmpty(original)){try{Count(unresolved,p.HasExited?"Exited":"StillPresent");}catch{Count(unresolved,"ExitStateUnavailable");}}
  }
  var report=new{capturedUtc=DateTime.UtcNow.ToString("o"),restrictedToken=Restricted(),is64Bit=Environment.Is64BitProcess,total=total,mainModule=module,limitedQuery=limited,recoveredPaths=recovered,pathDisagreements=disagreements,unresolved=unresolved,epsonStarted=false,note="Aggregate diagnostic only. No PID/path/endpoint values retained. No permissions changed. Not v8 evidence; snapshots race process changes."};
  string directory=Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"..","..","outputs"));
  string output=Path.Combine(directory,"process-access-desktop-"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff")+".json");
  using(var f=new FileStream(output,FileMode.CreateNew,FileAccess.Write))using(var w=new StreamWriter(f)){w.Write(new JavaScriptSerializer().Serialize(report));}
  Console.WriteLine(output);return 0;
 }catch(Exception e){Console.WriteLine("DIAGNOSTIC_ERROR "+e.GetType().Name+": "+e.Message);return 2;}}
}

