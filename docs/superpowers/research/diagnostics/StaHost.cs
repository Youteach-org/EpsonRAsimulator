using System;
using System.IO;
using System.Reflection;

// Diagnostic-only host. Invokes the unchanged reviewed worker on this STA thread.
internal static class StaHost
{
    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            var path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "EpsonRa.Bridge.Research.Worker.exe");
            var entry = Assembly.LoadFrom(path).EntryPoint;
            if (entry == null || entry.ReturnType != typeof(int))
                return 3;
            return (int)entry.Invoke(null, new object[] { args });
        }
        catch
        {
            Console.Out.WriteLine("{\"schemaVersion\":1,\"status\":\"FAILED\",\"success\":false,\"cleanup\":\"UNKNOWN\",\"error\":\"StaHostFailure\"}");
            return 3;
        }
    }
}
