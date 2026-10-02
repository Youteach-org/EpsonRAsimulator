using System;
using System.IO;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Supervisor;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class ReviewRegression
    {
        public static int Main(string[] args)
        {
            int failed = 0;
            foreach (var name in new[] { "PeTruncated", "PeUnbacked", "Missing", "Negative", "Reversed", "OutsideTrace", "FailureEvidence" })
                try { Check(name, args[0]); Console.WriteLine("PASS " + name); }
                catch (Exception e) { failed++; Console.WriteLine("FAIL " + name + ": " + e.Message); }
            return failed == 0 ? 0 : 1;
        }
        public static void Check(string name, string fixture)
        {
            if (name.StartsWith("Pe")) {
                var b = new byte[0x300]; b[0]=77; b[1]=90; Put(b,0x3c,0x80); Put(b,0x80,0x4550);
                Put(b,0x84,0x0001014c); Put(b,0x94,0xe0); Put(b,0x98,0x10b); Put(b,0xf4,16);
                Put(b,0x168,0x2000); Put(b,0x16c,0x48); Put(b,0x180,0x200); Put(b,0x184,0x2000);
                Put(b,0x188,name=="PeUnbacked"?1:0x100); Put(b,0x18c,0x200); Put(b,0x200,0x48); Put(b,0x210,9);
                if (name=="PeTruncated") Array.Resize(ref b,0x214);
                try { using(var stream=new MemoryStream(b)) PeImageInspector.Inspect(stream); }
                catch(InvalidDataException) { return; }
                throw new Exception("incomplete CLR header accepted");
            }
            var before = Sample(1);
            var after = Sample(20);
            if(name=="Missing") before=null;
            if(name=="Negative") after.OwnedProcessCount=-1;
            if(name=="Reversed") after.MonotonicTicks=0;
            if(name=="OutsideTrace") after.MonotonicTicks=1;
            var path=Path.GetTempFileName();
            try {
                if(name=="FailureEvidence") {
                    File.WriteAllText(path,"{\"stage\":\"LoadOnly\",\"installRoot\":\"C:\\\\SyntheticEpson\"}");
                    var result=WorkerSupervisor.Run(new WorkerRequest { WorkerPath=fixture,RequestPath=path,TimeoutSeconds=2,ExtraArguments=new[]{"structured-failure"} });
                    if(result.Success || result.ExitCode!=3 || result.WorkerResult==null || result.Cleanup!="CONFIRMED" || result.Observation==null || result.Observation.EventCount!=1)
                        throw new Exception("worker failure lost detached result/partial stage evidence");
                    return;
                }
                File.WriteAllLines(path,new[]{"{\"name\":\"before:Load\",\"monotonicTicks\":2}","{\"name\":\"after:Load\",\"monotonicTicks\":3}"});
                var result2=ExternalObservation.Evaluate(before,after,path,Stage.LoadOnly);
                if(result2.Conclusive || result2.Status!="INCONCLUSIVE") throw new Exception("invalid coverage accepted");
                if(name!="OutsideTrace" && (object)result2.OwnedProcessDelta!=null) throw new Exception("invalid evidence fabricated count");
            } finally { File.Delete(path); }
        }
        private static ObservationSnapshot Sample(long ticks) { return new ObservationSnapshot { MonotonicTicks=ticks,
            ProcessSampleAvailable=true,TcpSampleAvailable=true,TcpIpv6SampleAvailable=true,OwnershipUnambiguous=true,OwnedProcessCount=5 }; }
        private static void Put(byte[] bytes,int offset,int value) { for(int i=0;i<4;i++) bytes[offset+i]=(byte)(value>>(8*i)); }
    }
}

