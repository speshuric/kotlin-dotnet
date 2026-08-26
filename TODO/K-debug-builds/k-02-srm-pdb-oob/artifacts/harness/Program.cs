using System;
using System.IO;
using System.Linq;
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;

using var provider = MetadataReaderProvider.FromPortablePdbStream(new FileStream(args[0], FileMode.Open, FileAccess.Read, FileShare.Read));
var md = provider.GetMetadataReader();

Console.WriteLine($"Documents: {md.Documents.Count}");
foreach (var dh in md.Documents)
{
    var d = md.GetDocument(dh);
    try
    {
        var bytes = md.GetBlobBytes(d.Name);
        var text = System.Text.Encoding.UTF8.GetString(bytes.Skip(1).ToArray());
        Console.WriteLine($"  doc: {text} hashBytes={bytes.Length}");
    }
    catch (Exception e)
    {
        Console.WriteLine($"  doc NAME FAIL: {e.GetType().Name}: {e.Message}");
    }
}

Console.WriteLine($"MethodDebugInformation rows: {md.MethodDebugInformation.Count}");
foreach (var mh in md.MethodDebugInformation)
{
    var token = MetadataTokens.GetToken(mh);
    try
    {
        var mdi = md.GetMethodDebugInformation(mh);
        var sps = mdi.GetSequencePoints().ToList();
        var first = sps.Count > 0 ? $"first=({sps[0].Offset},{sps[0].StartLine}:{sps[0].StartColumn})" : "empty";
        Console.WriteLine($"  0x{token:X8} sp={sps.Count} {first}");
    }
    catch (Exception e)
    {
        var blob = mdi.SequencePoints.IsNil ? Array.Empty<byte>() : md.GetBlobBytes(mdi.SequencePoints);
        Console.WriteLine($"  0x{token:X8} FAIL {e.GetType().Name}: {e.Message}; blob({blob.Length})={Convert.ToHexString(blob)}");
    }
}

Console.WriteLine($"LocalScopes: {md.LocalScopes.Count}, LocalVariables: {md.LocalVariables.Count}");
