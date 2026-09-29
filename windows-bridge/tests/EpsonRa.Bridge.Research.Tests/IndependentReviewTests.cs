using System;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;
namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public sealed class IndependentReviewTests
    {
        [DataTestMethod]
        [DataRow("PeTruncated")][DataRow("PeUnbacked")][DataRow("Missing")][DataRow("Negative")]
        [DataRow("Reversed")][DataRow("OutsideTrace")][DataRow("FailureEvidence")]
        public void ReviewEvidenceBoundary(string name) { ReviewRegression.Check(name, Fixture()); }
        [DataTestMethod]
        [DataRow("Create")][DataRow("Started")][DataRow("Poll")][DataRow("Dispose")]
        public void ObservationCannotDefeatDeadline(string point) { ObservationDeadlineRegression.Check(Fixture(), point); }
        [TestMethod]
        public void DescriptorCannotCoerceVirtualType()
        {
            foreach(var value in new object[]{"3",2.6,true,3,DescriptorTypeRegression.Kind.Virtual}) DescriptorTypeRegression.Check(value);
        }
        private static string Fixture() { return Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
            "..","..","..","..","EpsonRa.Bridge.Research.Fixture","bin","Release","net48","EpsonRa.Bridge.Research.Fixture.exe")); }
    }
}

