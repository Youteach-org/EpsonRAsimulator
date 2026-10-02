using System;
using System.Reflection;
using EpsonRa.Bridge.Research.Worker;
namespace EpsonRa.Bridge.Research.Tests
{
    public static class DescriptorTypeRegression
    {
        public static int Main()
        {
            int failed=0;
            foreach(var value in new object[]{"3",2.6,true,3,Kind.Virtual})
                try { Check(value); Console.WriteLine("PASS " + value.GetType().Name); }
                catch(Exception e) { failed++; Console.WriteLine("FAIL " + value.GetType().Name + ": " + e.Message); }
            return failed==0?0:1;
        }
        public static void Check(object value)
        {
            var method=typeof(InstalledApiAdapter).GetMethod("ConvertConnection",BindingFlags.Static|BindingFlags.NonPublic);
            bool valid=value is int || value is Kind;
            try {
                var result=(NativeConnection)method.Invoke(null,new object[]{new Descriptor { ConnectionType=value }});
                if(!valid || result.TypeNumber!=3) throw new Exception("unsupported descriptor granted eligibility");
            } catch(TargetInvocationException e) {
                if(valid || !(e.InnerException is InvalidOperationException)) throw;
            }
        }
        public enum Kind { Virtual=3 }
        public sealed class Descriptor {
            public string ConnectionName { get { return "C4 Sample"; } }
            public int ConnectionNumber { get { return 2; } }
            public object ConnectionType { get; set; }
        }
    }
}

