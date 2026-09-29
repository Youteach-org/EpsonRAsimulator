using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;

namespace EpsonRa.Bridge.Research.Worker
{
    public sealed class InstalledApiAdapter : INativeApi
    {
        private Assembly _assembly;
        private Type _spelType;
        private object _spel;

        public void Load(string installRoot)
        {
            if (_assembly != null)
                throw new InvalidOperationException("Native assembly is already loaded.");
            if (string.IsNullOrWhiteSpace(installRoot) || !Path.IsPathRooted(installRoot))
                throw new ArgumentException("Install root must be absolute.", nameof(installRoot));

            var dllPath = Path.Combine(Path.GetFullPath(installRoot), "exe", "RCAPINet.dll");
            if (!File.Exists(dllPath))
                throw new FileNotFoundException("Installed RCAPINet assembly was not found.", dllPath);

            _assembly = Assembly.LoadFrom(dllPath);
            _spelType = _assembly.GetType("RCAPINet.Spel", true, false);
        }

        public void Construct()
        {
            RequireLoaded();
            if (_spel != null)
                throw new InvalidOperationException("Spel instance already exists.");
            _spel = Activator.CreateInstance(_spelType);
            if (_spel == null)
                throw new InvalidOperationException("Spel construction returned null.");
        }

        public void SetServerInstance(int instance)
        {
            RequireSpel();
            if (instance < 1 || instance > 10)
                throw new ArgumentOutOfRangeException(nameof(instance));

            var property = _spelType.GetProperty(
                "ServerInstance",
                BindingFlags.Instance | BindingFlags.Public);

            if (property == null ||
                !property.CanWrite ||
                property.PropertyType != typeof(int) ||
                property.GetIndexParameters().Length != 0)
                throw new MissingMemberException("Required ServerInstance property is unavailable.");

            property.SetValue(_spel, instance, null);
        }

        public void Initialize()
        {
            InvokeExact("Initialize", Type.EmptyTypes, new object[0]);
        }

        public IReadOnlyList<NativeConnection> GetConnections()
        {
            var value = InvokeExact("GetConnectionInfo", Type.EmptyTypes, new object[0]);
            var enumerable = value as IEnumerable;
            if (enumerable == null || value is string)
                throw new InvalidOperationException("Connection inventory shape is unsupported.");

            var result = new List<NativeConnection>();
            foreach (var item in enumerable)
            {
                if (item == null)
                    throw new InvalidOperationException("Connection inventory contains null.");
                result.Add(ConvertConnection(item));
            }
            return result;
        }

        public void ConnectByName(string name)
        {
            if (!string.Equals(name, "C4 Sample", StringComparison.Ordinal))
                throw new InvalidOperationException("Only the exact approved Virtual target name is allowed.");

            InvokeExact("Connect", new[] { typeof(string) }, new object[] { name });
        }

        public NativeConnection GetCurrentConnection()
        {
            var value = InvokeExact("GetCurrentConnectionInfo", Type.EmptyTypes, new object[0]);
            if (value == null)
                return null;
            return ConvertConnection(value);
        }

        public void Disconnect()
        {
            InvokeExact("Disconnect", Type.EmptyTypes, new object[0]);
        }

        public void Dispose()
        {
            if (_spel == null)
                return;

            var instance = _spel;
            _spel = null;
            InvokeExactOn(instance, "Dispose", Type.EmptyTypes, new object[0]);
        }

        private object InvokeExact(string methodName, Type[] parameterTypes, object[] arguments)
        {
            RequireSpel();
            return InvokeExactOn(_spel, methodName, parameterTypes, arguments);
        }

        private object InvokeExactOn(object target, string methodName, Type[] parameterTypes, object[] arguments)
        {
            var targetType = target.GetType();
            var matches = targetType.GetMethods(BindingFlags.Instance | BindingFlags.Public)
                .Where(method =>
                    method.Name == methodName &&
                    !method.IsGenericMethodDefinition &&
                    ParametersMatch(method.GetParameters(), parameterTypes))
                .ToArray();

            if (matches.Length != 1)
                throw new MissingMethodException("Required native method overload is unavailable or ambiguous.");

            return matches[0].Invoke(target, arguments);
        }

        private static bool ParametersMatch(ParameterInfo[] parameters, Type[] expectedTypes)
        {
            if (parameters.Length != expectedTypes.Length)
                return false;
            for (var i = 0; i < parameters.Length; i++)
            {
                if (parameters[i].ParameterType != expectedTypes[i])
                    return false;
            }
            return true;
        }

        private static NativeConnection ConvertConnection(object value)
        {
            var type = value.GetType();
            var nameProperty = type.GetProperty("ConnectionName", BindingFlags.Instance | BindingFlags.Public);
            var numberProperty = type.GetProperty("ConnectionNumber", BindingFlags.Instance | BindingFlags.Public);
            var typeProperty = type.GetProperty("ConnectionType", BindingFlags.Instance | BindingFlags.Public);

            if (nameProperty == null || numberProperty == null || typeProperty == null ||
                nameProperty.GetIndexParameters().Length != 0 ||
                numberProperty.GetIndexParameters().Length != 0 ||
                typeProperty.GetIndexParameters().Length != 0)
                throw new MissingMemberException("Connection descriptor contract is incomplete.");

            var nameValue = nameProperty.GetValue(value, null);
            var numberValue = numberProperty.GetValue(value, null);
            var typeValue = typeProperty.GetValue(value, null);

            if (!(nameValue is string) || numberValue == null || typeValue == null)
                throw new InvalidOperationException("Connection descriptor values are invalid.");

            var code = Type.GetTypeCode(typeValue.GetType());
            if (code != TypeCode.SByte && code != TypeCode.Byte && code != TypeCode.Int16 &&
                code != TypeCode.UInt16 && code != TypeCode.Int32 && code != TypeCode.UInt32 &&
                code != TypeCode.Int64 && code != TypeCode.UInt64)
                throw new InvalidOperationException("Connection type must be an integral value or enum.");

            return new NativeConnection
            {
                Name = (string)nameValue,
                ConnectionNumber = Convert.ToInt32(numberValue),
                TypeNumber = Convert.ToInt32(typeValue),
                TypeName = typeValue.ToString()
            };
        }

        private void RequireLoaded()
        {
            if (_assembly == null || _spelType == null)
                throw new InvalidOperationException("Native assembly is not loaded.");
        }

        private void RequireSpel()
        {
            RequireLoaded();
            if (_spel == null)
                throw new InvalidOperationException("Spel instance is not constructed.");
        }
    }
}

