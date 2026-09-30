using System;
using System.IO;
using System.Reflection;
using System.Threading;

// Proprietary-free fixture. Never copied into the Epson installation.
namespace RCAPINet
{
    public sealed class Spel
    {
        private int instance;
        private static void Record(string value)
        {
            File.AppendAllText(Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location), "calls.txt"), value + "\n");
        }
        public Spel() { Record("Construct"); }
        public int ServerInstance { set { instance = value; Record("ServerInstance=" + value); } }
        public void Initialize()
        {
            Record("Initialize:" + Thread.CurrentThread.GetApartmentState() + ":" + (IntPtr.Size * 8));
            if (instance != 10 || IntPtr.Size != 4 || Thread.CurrentThread.GetApartmentState() != ApartmentState.STA)
                throw new InvalidOperationException("Fixture requires STA x86 instance 10");
        }
        public void Dispose() { Record("Dispose"); }
    }
}
