using System;
using System.Collections.Generic;

namespace EpsonRa.Bridge.Research.Worker
{
    public sealed class InstalledApiAdapter : INativeApi
    {
        public void Load(string installRoot) { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void Construct() { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void SetServerInstance(int instance) { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void Initialize() { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public IReadOnlyList<NativeConnection> GetConnections() { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void ConnectByName(string name) { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public NativeConnection GetCurrentConnection() { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void Disconnect() { throw new NotSupportedException("Native adapter execution requires an approved native stage."); }
        public void Dispose() { }
    }
}
