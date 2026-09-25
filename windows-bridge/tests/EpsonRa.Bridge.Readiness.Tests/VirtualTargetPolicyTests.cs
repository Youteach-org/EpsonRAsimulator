using System;
using System.Collections.Generic;
using System.Linq;
using EpsonRa.Bridge.Readiness;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Readiness.Tests
{
    [TestClass]
    public class VirtualTargetPolicyTests
    {
        [TestMethod]
        public void ExactRequestedVirtualIdentityIsEligible()
        {
            Assert.AreEqual(
                Eligibility.Eligible,
                VirtualTargetPolicy.Select(
                    "v1",
                    new[] { new TargetDescriptor("v1", TargetKind.Virtual) }));
        }

        [TestMethod]
        public void ExactPhysicalOrUnknownIdentityIsRejected()
        {
            Assert.AreEqual(
                Eligibility.Rejected,
                VirtualTargetPolicy.Select(
                    "p1",
                    new[] { new TargetDescriptor("p1", TargetKind.Physical) }));
            Assert.AreEqual(
                Eligibility.Rejected,
                VirtualTargetPolicy.Select(
                    "u1",
                    new[] { new TargetDescriptor("u1", TargetKind.Unknown) }));
        }

        [TestMethod]
        public void DuplicateRequestedIdentityIsAmbiguousEvenWhenKindsDiffer()
        {
            Assert.AreEqual(
                Eligibility.Ambiguous,
                VirtualTargetPolicy.Select(
                    "v1",
                    new[] {
                        new TargetDescriptor("v1", TargetKind.Virtual),
                        new TargetDescriptor("v1", TargetKind.Unknown)
                    }));
        }

        [TestMethod]
        public void RequestedIdentityMatchingIsOrdinalAndCaseSensitive()
        {
            Assert.AreEqual(
                Eligibility.Missing,
                VirtualTargetPolicy.Select(
                    "V1",
                    new[] { new TargetDescriptor("v1", TargetKind.Virtual) }));
        }

        [TestMethod]
        public void BlankRequestedIdentityIsRejected()
        {
            foreach (var id in new[] { null, string.Empty, "   " })
            {
                Assert.AreEqual(
                    Eligibility.Rejected,
                    VirtualTargetPolicy.Select(
                        id,
                        new[] { new TargetDescriptor("v1", TargetKind.Virtual) }));
            }
        }

        [TestMethod]
        public void NullCandidateCollectionOrEntryIsRejected()
        {
            Assert.AreEqual(
                Eligibility.Rejected,
                VirtualTargetPolicy.Select("v1", null));

            Assert.AreEqual(
                Eligibility.Rejected,
                VirtualTargetPolicy.Select(
                    "v1",
                    new TargetDescriptor[] {
                        new TargetDescriptor("v1", TargetKind.Virtual),
                        null
                    }));
        }

        [TestMethod]
        public void EmptyOrNonMatchingCandidatesAreMissing()
        {
            Assert.AreEqual(
                Eligibility.Missing,
                VirtualTargetPolicy.Select(
                    "v1",
                    Array.Empty<TargetDescriptor>()));

            Assert.AreEqual(
                Eligibility.Missing,
                VirtualTargetPolicy.Select(
                    "v1",
                    new[] { new TargetDescriptor("other", TargetKind.Virtual) }));
        }

        [TestMethod]
        public void CallerMutationAfterSelectionCannotChangeReturnedOutcome()
        {
            var candidates = new List<TargetDescriptor> {
                new TargetDescriptor("v1", TargetKind.Virtual)
            };

            var result = VirtualTargetPolicy.Select("v1", candidates);
            candidates.Add(new TargetDescriptor("v1", TargetKind.Physical));

            Assert.AreEqual(Eligibility.Eligible, result);
            Assert.AreEqual(
                Eligibility.Ambiguous,
                VirtualTargetPolicy.Select("v1", candidates));
        }

        [TestMethod]
        public void EligibilityDoesNotRepresentConnectionAuthority()
        {
            CollectionAssert.DoesNotContain(
                Enum.GetNames(typeof(Eligibility)),
                "Connected");
        }

        [TestMethod]
        public void DescriptorIdentityIsDetachedFromNormalizationOrFallback()
        {
            Assert.AreEqual(
                Eligibility.Missing,
                VirtualTargetPolicy.Select(
                    " v1 ",
                    new[] { new TargetDescriptor("v1", TargetKind.Virtual) }));
        }
    }
}
