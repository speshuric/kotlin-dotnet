// Ground-truth verifier: opens PE images built by kotlin-dotnet-engine/dotnetutils
// with the real System.Reflection.Metadata stack and validates structure.
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;

if (args.Length < 1)
{
    Console.Error.WriteLine("usage: verifier <path-to-assembly>");
    return 2;
}

using var fs = File.OpenRead(args[0]);
using var pe = new PEReader(fs);

var coff = pe.PEHeaders.CoffHeader!;
var peHdr = pe.PEHeaders.PEHeader!;
Console.WriteLine($"COFF: machine={coff.Machine} sections={coff.NumberOfSections}");
Console.WriteLine($"PE: magic={peHdr.Magic} subsystem={peHdr.Subsystem} entrypointRva=0x{peHdr.AddressOfEntryPoint:X}");

var cor = pe.PEHeaders.CorHeader!;
Console.WriteLine($"COR: flags={cor.Flags} entrypointToken=0x{cor.EntryPointTokenOrRelativeVirtualAddress:X} mdSize={cor.MetadataDirectory.Size}");

var md = pe.GetMetadataReader();

var module = md.GetModuleDefinition();
Console.WriteLine($"Module: {md.GetString(module.Name)} mvid={md.GetGuid(module.Mvid)}");

foreach (var h in md.TypeDefinitions)
{
    var td = md.GetTypeDefinition(h);
    Console.WriteLine($"Type: {md.GetString(td.Namespace)}.{md.GetString(td.Name)}");
    foreach (var mh in td.GetMethods())
    {
        var m = md.GetMethodDefinition(mh);
        var sig = md.GetBlobBytes(m.Signature);
        Console.WriteLine($"  Method: {md.GetString(m.Name)} rva=0x{m.RelativeVirtualAddress:X} sig={BitConverter.ToString(sig)}");
        var body = pe.GetMethodBody(m.RelativeVirtualAddress);
        var il = body.GetILReader();
        Console.Write("  IL:");
        while (il.RemainingBytes > 0) Console.Write($" {il.ReadByte():X2}");
        Console.WriteLine();
    }
}

foreach (var h in md.AssemblyReferences)
{
    var ar = md.GetAssemblyReference(h);
    Console.WriteLine($"AssemblyRef: {md.GetString(ar.Name)} {ar.Version}");
}

foreach (var h in md.MemberReferences)
{
    var mr = md.GetMemberReference(h);
    Console.WriteLine($"MemberRef: {md.GetString(mr.Name)} parent-kind={mr.Parent.Kind}");
}

Console.WriteLine("VERIFIER OK");
return 0;
