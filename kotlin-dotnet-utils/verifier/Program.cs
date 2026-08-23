// Ground-truth verifier: opens PE images built by kotlin-dotnet-engine/dotnetutils
// with the real System.Reflection.Metadata stack and validates structure.
using System.Reflection.Metadata;
using System.Reflection.Metadata.Ecma335;
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

Console.WriteLine(
    $"RowCounts: TypeRef={md.TypeReferences.Count} TypeDef={md.TypeDefinitions.Count} " +
    $"Field={md.FieldDefinitions.Count} MethodDef={md.MethodDefinitions.Count} " +
    $"MemberRef={md.MemberReferences.Count} AssemblyRef={md.AssemblyReferences.Count}");

foreach (var h in md.TypeDefinitions)
{
    var td = md.GetTypeDefinition(h);
    var methods = td.GetMethods().ToList();
    var fields = td.GetFields().ToList();
    var methodRange = methods.Count > 0
        ? $"{MetadataTokens.GetRowNumber(methods[0])}..{MetadataTokens.GetRowNumber(methods[^1]) + 1}"
        : "empty";
    var fieldRange = fields.Count > 0
        ? $"{MetadataTokens.GetRowNumber(fields[0])}.."
        : "empty";
    Console.WriteLine(
        $"Type: {md.GetString(td.Namespace)}.{md.GetString(td.Name)} " +
        $"attrs=0x{(int)td.Attributes:X8} extends={Describe(md, td.BaseType)} " +
        $"fieldList={fieldRange} methodList={methodRange}");
    foreach (var ih in td.GetInterfaceImplementations())
    {
        var iface = md.GetInterfaceImplementation(ih);
        Console.WriteLine($"  Implements: {Describe(md, iface.Interface)}");
    }
    foreach (var fh in fields)
    {
        var f = md.GetFieldDefinition(fh);
        var fsig = md.GetBlobBytes(f.Signature);
        Console.WriteLine($"  Field: {md.GetString(f.Name)} attrs=0x{(int)f.Attributes:X4} sig={BitConverter.ToString(fsig)}");
    }
    foreach (var mh in methods)
    {
        var m = md.GetMethodDefinition(mh);
        var sig = md.GetBlobBytes(m.Signature);
        Console.WriteLine(
            $"  Method: {md.GetString(m.Name)} rva=0x{m.RelativeVirtualAddress:X} " +
            $"attrs=0x{(int)m.Attributes:X4} implAttrs=0x{(int)m.ImplAttributes:X4} " +
            $"sig={BitConverter.ToString(sig)}");
        if (m.RelativeVirtualAddress == 0) continue;
        var body = pe.GetMethodBody(m.RelativeVirtualAddress);
        var il = body.GetILReader();
        Console.Write("  IL:");
        while (il.RemainingBytes > 0) Console.Write($" {il.ReadByte():X2}");
        Console.WriteLine();
    }
}

foreach (var h in md.TypeReferences)
{
    var tr = md.GetTypeReference(h);
    var scope = tr.ResolutionScope.Kind switch
    {
        HandleKind.AssemblyReference => $"asm={md.GetString(md.GetAssemblyReference((System.Reflection.Metadata.AssemblyReferenceHandle)tr.ResolutionScope).Name)}",
        HandleKind.ModuleReference => "modref",
        HandleKind.TypeReference => "typeref",
        HandleKind.ModuleDefinition => "module",
        _ => "nil",
    };
    Console.WriteLine($"TypeRef: {md.GetString(tr.Namespace)}.{md.GetString(tr.Name)} scope={scope}");
}

static string Describe(MetadataReader md, EntityHandle handle)
{
    if (handle.IsNil) return "nil";
    return handle.Kind switch
    {
        HandleKind.TypeDefinition => $"TypeDef#{MetadataTokens.GetRowNumber(handle)}",
        HandleKind.TypeReference => $"TypeRef#{MetadataTokens.GetRowNumber(handle)}({NameOf(md, (TypeReferenceHandle)handle)})",
        _ => $"{handle.Kind}",
    };
}

static string NameOf(MetadataReader md, TypeReferenceHandle h)
{
    var tr = md.GetTypeReference(h);
    return $"{md.GetString(tr.Namespace)}.{md.GetString(tr.Name)}";
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
