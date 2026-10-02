using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;
class DesktopInitializeOnce {
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool OpenProcessToken(IntPtr process,uint access,out IntPtr token);
 [DllImport("advapi32.dll",SetLastError=true)] static extern bool IsTokenRestricted(IntPtr token);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 static void Hash(string path,string expected){using(var sha=SHA256.Create())using(var f=File.OpenRead(path)){if(BitConverter.ToString(sha.ComputeHash(f)).Replace("-","")!=expected)throw new InvalidOperationException("Artifact mismatch: "+Path.GetFileName(path));}}
 static bool Epson(Process p){return p.ProcessName.Equals("erc70",StringComparison.OrdinalIgnoreCase)||p.ProcessName.Equals("erc70PServer",StringComparison.OrdinalIgnoreCase)||p.ProcessName.StartsWith("EpsonRa.",StringComparison.OrdinalIgnoreCase);}
 static void Context(){
  IntPtr token;if(!OpenProcessToken(Process.GetCurrentProcess().Handle,8,out token))throw new InvalidOperationException("Cannot inspect execution token.");
  try{if(IsTokenRestricted(token))throw new InvalidOperationException("Restricted context: no Epson launch. Run manually in normal desktop session.");}finally{CloseHandle(token);}
  using(var key=Registry.CurrentUser.OpenSubKey("Software",true)){if(key==null)throw new InvalidOperationException("HKCU write handle unavailable.");}
  using(var machine=RegistryKey.OpenBaseKey(RegistryHive.LocalMachine,RegistryView.Registry32)){
   using(var net=machine.OpenSubKey(@"SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full")){if(net==null||Convert.ToInt64(net.GetValue("Release",0))<528040)throw new InvalidOperationException("net48 required.");}
   using(var controller=machine.OpenSubKey(@"SOFTWARE\SEIKO EPSON CORPORATION\EPSON RC+ 7.0\Controller")){if(controller==null||!string.Equals(Convert.ToString(controller.GetValue("AutoConnect")),"False",StringComparison.OrdinalIgnoreCase))throw new InvalidOperationException("Machine AutoConnect is not confirmed OFF.");}
  }
  using(var user=RegistryKey.OpenBaseKey(RegistryHive.CurrentUser,RegistryView.Registry32))using(var controller=user.OpenSubKey(@"SOFTWARE\SEIKO EPSON CORPORATION\EPSON RC+ 7.0\Controller")){
   var value=controller==null?null:controller.GetValue("AutoConnect");if(value!=null&&!string.Equals(Convert.ToString(value),"False",StringComparison.OrdinalIgnoreCase))throw new InvalidOperationException("User AutoConnect override is not OFF.");
  }
  foreach(var p in Process.GetProcesses())using(p){if(Epson(p))throw new InvalidOperationException("Existing Epson/research process. No launch.");}
 }
 static void NewJson(string path,object data){using(var f=new FileStream(path,FileMode.CreateNew,FileAccess.Write))using(var w=new StreamWriter(f)){w.Write(new JavaScriptSerializer().Serialize(data));}}
 [STAThread] static int Main(string[] args){
  bool check=args.Length==1&&args[0]=="--check";
  if(args.Length>0&&!check)return 64;
  string root=AppDomain.CurrentDomain.BaseDirectory,run=Path.Combine(root,"native-desktop-v8");
  try{
   Context();
   string supervisor=Path.Combine(root,@"observer-diagnosis\sealed\EpsonRa.Bridge.Research.Supervisor.exe"),worker=Path.Combine(root,@"native-capture-sealed\x86\EpsonRa.Bridge.Research.Worker.exe"),request=Path.Combine(root,@"native-capture-sealed\initialize-observe.proposed.json");
   Hash(supervisor,"763458C8057D7312B178F2AD9E577FEFBCCCAA9DC5259EEFF5A68B62F3C86661");
   Hash(supervisor+".config","051099983B896673909E01A1F631B6652ABB88DA95C9F06F3EFEF4BE033091FA");
   Hash(worker,"0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4");
   Hash(Path.Combine(Path.GetDirectoryName(supervisor),"EpsonRa.Bridge.Research.dll"),"ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF");
   Hash(Path.Combine(Path.GetDirectoryName(worker),"EpsonRa.Bridge.Research.dll"),"ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF");
   Hash(request,"410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B");
   if(check){Console.WriteLine("PREFLIGHT_PASS_NO_NATIVE_LAUNCH");return 0;}
   Directory.CreateDirectory(run);string result=Path.Combine(run,"initialize-observe-v8.result.json"),receipt=Path.Combine(run,"initialize-observe-v8.execution.json");
   int code=DesktopLaunchCore.Run(supervisor,"--worker \""+worker+"\" --request \""+request+"\" --timeout-seconds 30 --result-file \""+result+"\"",receipt,result,45000);
   var residual=new List<object>();foreach(var p in Process.GetProcesses())using(p){if(Epson(p)){string start=null;try{start=p.StartTime.ToUniversalTime().ToString("o");}catch{}residual.Add(new{pid=p.Id,name=p.ProcessName,startUtc=start});}}
   NewJson(Path.Combine(run,"initialize-observe-v8.postflight.local.json"),new{capturedUtc=DateTime.UtcNow.ToString("o"),processes=residual,launcherExitCode=code});
   MessageBox.Show("El lanzador terminó. No repitas la prueba.\n\nAvísale a Codex: listo.\n\nCódigo del lanzador: "+code+"\nResultado y posibles procesos activos pendientes de revisión.\n\n"+run,"Epson: revisar resultado",MessageBoxButtons.OK,MessageBoxIcon.Information);return code;
  }catch(Exception e){
   string message=e.GetType().Name+": "+e.Message;Console.WriteLine(message);
   try{NewJson(Path.Combine(root,"..","outputs","desktop-initialize-error-"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff")+".json"),new{capturedUtc=DateTime.UtcNow.ToString("o"),error=message});}catch{}
   if(!check)MessageBox.Show("Prueba detenida; no repetir.\n\n"+message,"Epson: revisar diagnóstico",MessageBoxButtons.OK,MessageBoxIcon.Warning);return 3;
  }
 }
}
