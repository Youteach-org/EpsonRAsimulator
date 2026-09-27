using System.Collections.Generic;

namespace EpsonRa.Bridge.Readiness
{
    public interface IReadinessEnvironment
    {
        IReadOnlyList<string> DiscoverRoots();
        IReadOnlyList<ReadinessCheck> InspectRoot(string root);
    }
}
