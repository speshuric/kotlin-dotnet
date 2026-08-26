using System;
using System.IO;
using System.Linq;
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;
using System.Reflection.Metadata.Ecma335;

using var provider = MetadataReaderProvider.FromPortablePdbStream(new FileStream(args[0], FileMode.Open, FileAccess.Read, FileShare.Read));
var md = provider.GetMetadataReader();

Console.WriteLine($"Documents: {md.Documents.Count}");
foreach (var dh in md.Documents)
{
    var d = md.GetDocument(dh);
    var hashLen = d.Hash.IsNil ? 0 : md.GetBlobBytes(d.Hash).Length;
    Console.WriteLine($"  doc: {md.GetString(d.Name)} hashAlgo={d.HashAlgorithm} hashBytes={hashLen}");
}

Console.WriteLine($"MethodDebugInformation rows: {md.MethodDebugInformation.Count}");
int totalSp = 0;
foreach (var h in md.MethodDebugInformation)
{
    var mdi = md.GetMethodDebugInformation(h);
    var sps = mdi.GetSequencePoints().ToList();
    totalSp += sps.Count;
    if (sps.Count > 0)
    {
        var token = MetadataTokens.GetToken(h);
        var docSet = mdi.Document.IsNil ? "nil" : "set";
        Console.WriteLine($"  method token=0x{token:X8} doc={docSet} spCount={sps.Count}");
        foreach (var sp in sps.Take(3))
            Console.WriteLine($"    offset={sp.Offset} lines {sp.StartLine}:{sp.StartColumn}-{sp.EndLine}:{sp.EndColumn}");
    }
}
Console.WriteLine($"LocalScopes: {md.LocalScopes.Count}, LocalVariables: {md.LocalVariables.Count}");
Console.WriteLine($"TOTAL sequence points: {totalSp}");
