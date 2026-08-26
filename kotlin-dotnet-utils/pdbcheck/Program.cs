using System;
using System.IO;
using System.Linq;
using System.Reflection.Metadata;

// Portable-PDB readability gate: opens the sidecar PDB with the real SRM,
// walks Documents / MethodDebugInformation / LocalScope tables and fails
// loudly (exit 1) on any parse error.
using var provider = MetadataReaderProvider.FromPortablePdbStream(new FileStream(args[0], FileMode.Open, FileAccess.Read, FileShare.Read));
var md = provider.GetMetadataReader();

var documents = 0;
foreach (var dh in md.Documents)
{
    var d = md.GetDocument(dh);
    var nameBytes = md.GetBlobBytes(d.Name);
    var hashBytes = d.Hash.IsNil ? 0 : md.GetBlobBytes(d.Hash).Length;
    if (nameBytes == null || hashBytes == 0) throw new BadImageFormatException($"document {documents}: empty name or hash");
    documents++;
}

var methods = 0;
var seqPoints = 0;
foreach (var mh in md.MethodDebugInformation)
{
    var sps = md.GetMethodDebugInformation(mh).GetSequencePoints().ToList();
    foreach (var sp in sps)
    {
        if (sp.StartLine <= 0 || sp.StartColumn <= 0) throw new BadImageFormatException($"method {methods}: non-positive start position");
    }
    seqPoints += sps.Count;
    methods++;
}

Console.WriteLine($"PDB OK: documents={documents} methodDebugInfo={methods} sequencePoints={seqPoints} localScopes={md.LocalScopes.Count} localVariables={md.LocalVariables.Count}");
