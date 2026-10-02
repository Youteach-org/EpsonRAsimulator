using System;
using System.IO;
using System.Threading;
class DesktopLaunchFixture {
 static int Main(string[] args){if(args.Length==4){using(var receipt=new FileStream(args[3],FileMode.Open,FileAccess.Read,FileShare.ReadWrite)){if(receipt.Length==0)return 91;}}File.WriteAllText(args[0],System.Diagnostics.Process.GetCurrentProcess().Id.ToString());Thread.Sleep(int.Parse(args[1]));return int.Parse(args[2]);}
}
