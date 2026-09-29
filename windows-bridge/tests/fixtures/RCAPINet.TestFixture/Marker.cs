namespace EpsonRa.Bridge.TestFixture
{
    public sealed class Marker { }
}

namespace RCAPINet
{
    public enum SyntheticConnectionType
    {
        USB = 1,
        Ethernet = 2,
        Virtual = 3
    }

    public sealed class ConnectionInfo
    {
        public string ConnectionName { get; set; }
        public int ConnectionNumber { get; set; }
        public SyntheticConnectionType ConnectionType { get; set; }
    }

    public sealed class Spel
    {
        public int ServerInstance { get; set; }
        public object Robot { get; set; }

        public void Initialize()
        {
            if (ServerInstance < 1 || ServerInstance > 10)
                throw new System.InvalidOperationException("Synthetic server instance was not set.");
        }

        public ConnectionInfo[] GetConnectionInfo()
        {
            return new[]
            {
                new ConnectionInfo { ConnectionName = "Physical", ConnectionNumber = 3, ConnectionType = SyntheticConnectionType.Ethernet },
                new ConnectionInfo { ConnectionName = "C4 Sample", ConnectionNumber = 2, ConnectionType = SyntheticConnectionType.Virtual }
            };
        }

        public ConnectionInfo[] GetConnectionInfo(int forbidden)
        {
            throw new System.InvalidOperationException("Numeric GetConnectionInfo overload must not be used.");
        }

        public void Connect(string name)
        {
            if (name != "C4 Sample")
                throw new System.InvalidOperationException("Only the exact synthetic Virtual name is allowed.");
        }

        public void Connect(int number)
        {
            throw new System.InvalidOperationException("Numeric Connect overload must not be used.");
        }

        public ConnectionInfo GetCurrentConnectionInfo()
        {
            return new ConnectionInfo
            {
                ConnectionName = "C4 Sample",
                ConnectionNumber = 2,
                ConnectionType = SyntheticConnectionType.Virtual
            };
        }

        public void Disconnect() { }
        public void Dispose() { }

        public void Go()
        {
            throw new System.InvalidOperationException("Arbitrary native methods are forbidden.");
        }
    }
}
