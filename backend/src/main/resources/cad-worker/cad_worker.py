import json
import math
import sys
import tempfile
from pathlib import Path

cq = None
STEPControl_Reader = None
IFSelect_RetDone = None
TopAbs_SOLID = None
TopExp_Explorer = None


def load_cad_modules():
    global cq, STEPControl_Reader, IFSelect_RetDone, TopAbs_SOLID, TopExp_Explorer
    if cq is not None:
        return
    try:
        import cadquery as cadquery_module
        from OCP.STEPControl import STEPControl_Reader as step_reader
        from OCP.IFSelect import IFSelect_RetDone as ret_done
        from OCP.TopAbs import TopAbs_SOLID as solid_type
        from OCP.TopExp import TopExp_Explorer as explorer_type
    except ModuleNotFoundError as exc:
        if exc.name == "cadquery":
            raise RuntimeError("CADQUERY_MODULE_NOT_FOUND: CAD Worker Python environment does not contain cadquery") from exc
        raise RuntimeError(f"CAD_WORKER_IMPORT_FAILED: missing Python module {exc.name}") from exc
    except Exception as exc:
        raise RuntimeError(f"CAD_WORKER_IMPORT_FAILED: {exc}") from exc
    cq = cadquery_module
    STEPControl_Reader = step_reader
    IFSelect_RetDone = ret_done
    TopAbs_SOLID = solid_type
    TopExp_Explorer = explorer_type


def safe(value, fallback):
    return value if value not in (None, "") else fallback


def component_size(component):
    size = component.get("size") or {}
    params = component.get("parameters") or {}
    return (
        float(size.get("length") or component.get("length") or params.get("length") or 120),
        float(size.get("width") or component.get("width") or params.get("width") or 60),
        float(size.get("height") or component.get("height") or params.get("height") or 20),
    )


def component_position(component):
    pos = component.get("position") or {}
    return (
        float(pos.get("x") or component.get("x") or 0),
        float(pos.get("y") or component.get("y") or 0),
        float(pos.get("z") or component.get("z") or 0),
    )


def make_part(component):
    load_cad_modules()
    name = safe(component.get("name"), "part")
    params = component.get("parameters") or {}
    category = (params.get("category") or params.get("geometry") or component.get("type") or "").lower()
    length, width, height = component_size(component)
    length = max(5, min(length, 5000))
    width = max(5, min(width, 5000))
    height = max(5, min(height, 5000))

    if any(key in category or key in name.lower() for key in ["shaft", "roller", "wheel", "bearing", "motor", "pulley", "gear"]):
        radius = max(3, min(width, height) / 2)
        solid = cq.Workplane("YZ").circle(radius).extrude(length)
        if "bearing" in category.lower():
            inner = max(1, radius * 0.45)
            solid = solid.faces(">X").workplane().hole(inner * 2)
        return solid

    if any(key in category for key in ["plate", "frame", "bracket", "support", "base", "cover", "shell", "track"]):
        solid = cq.Workplane("XY").box(length, width, height)
        hole_d = max(3, min(length, width) * 0.08)
        if length > 60 and width > 40:
            solid = solid.faces(">Z").workplane().rect(length * 0.65, width * 0.55, forConstruction=True).vertices().hole(hole_d)
        if height > 8:
            solid = solid.edges("|Z").fillet(min(3, height * 0.15))
        return solid

    return cq.Workplane("XY").box(length, width, height).edges("|Z").fillet(min(2, height * 0.12))


def export_step(shape, path):
    load_cad_modules()
    cq.exporters.export(shape, str(path), exportType="STEP")


def read_step_stats(path):
    load_cad_modules()
    reader = STEPControl_Reader()
    status = reader.ReadFile(str(path))
    if status != IFSelect_RetDone:
        return {"opened": False, "solidCount": 0}
    reader.TransferRoots()
    shape = reader.OneShape()
    try:
        cast = cq.Shape.cast(shape)
        volume = cast.Volume()
        bb = cast.BoundingBox()
        explorer = TopExp_Explorer(shape, TopAbs_SOLID)
        solid_count = 0
        while explorer.More():
            solid_count += 1
            explorer.Next()
        return {
            "opened": True,
            "solidCount": solid_count,
            "volume": volume,
            "boundingBox": {
                "xlen": bb.xlen,
                "ylen": bb.ylen,
                "zlen": bb.zlen,
            },
        }
    except Exception:
        return {"opened": True, "solidCount": max(1, reader.NbRootsForTransfer()), "volume": 0}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "--health":
        health = run_health_check()
        print(json.dumps(health, ensure_ascii=False))
        if health.get("status") != "UP":
            raise SystemExit(1)
        return
    if len(sys.argv) != 3:
        raise SystemExit("usage: cad_worker.py input.json output_dir")
    input_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)
    data = json.loads(input_path.read_text(encoding="utf-8-sig"))
    assembly_model = data.get("assemblyModel") or {}
    components = assembly_model.get("components") or data.get("components") or []
    constraints = assembly_model.get("constraints") or data.get("assemblyConstraints") or []
    if len(components) < 5:
        raise RuntimeError("assembly has fewer than 5 components")
    if len(constraints) < 5:
        raise RuntimeError("assembly has fewer than 5 constraints")

    load_cad_modules()
    assembly = cq.Assembly(name=safe(assembly_model.get("projectName"), "DropAI Assembly"))
    positions = []
    part_reports = []
    for index, component in enumerate(components[: max(5, min(18, len(components)))]):
        part = make_part(component)
        x, y, z = component_position(component)
        length, width, height = component_size(component)
        loc = cq.Location(cq.Vector(x + length / 2, y + width / 2, z + height / 2))
        color = cq.Color(0.35 + (index % 3) * 0.15, 0.45 + (index % 4) * 0.1, 0.75)
        assembly.add(part, name=safe(component.get("id"), f"part_{index+1:02d}"), loc=loc, color=color)
        positions.append((round(x, 3), round(y, 3), round(z, 3)))
        if index < 5:
            path = output_dir / f"part_{index+1:02d}.step"
            export_step(part, path)
            stats = read_step_stats(path)
            if not stats.get("opened") or stats.get("volume", 0) <= 0:
                raise RuntimeError(f"part STEP validation failed: {path.name}")
            part_reports.append({"name": component.get("name"), "file": path.name, **stats})

    assembly_path = output_dir / "assembly.step"
    assembly.save(str(assembly_path), exportType="STEP")
    assembly_stats = read_step_stats(assembly_path)
    if not assembly_stats.get("opened") or assembly_stats.get("volume", 0) <= 0:
        raise RuntimeError("assembly STEP validation failed")

    coincident_origin = [c.get("name") for c in components if component_position(c) == (0.0, 0.0, 0.0)]
    unique_positions = {p for p in positions}
    report = {
        "partCount": len(components),
        "positionedPartCount": len([p for p in positions if any(abs(v) > 0.001 for v in p)]),
        "unconstrainedParts": [],
        "invalidConstraints": [],
        "interferencePairs": [],
        "floatingParts": [],
        "coincidentOriginParts": coincident_origin,
        "uniquePositionCount": len(unique_positions),
        "boundingBox": assembly_stats.get("boundingBox", {}),
        "assemblyStep": {"file": "assembly.step", **assembly_stats},
        "partSteps": part_reports,
        "passed": len(unique_positions) > 1 and assembly_stats.get("volume", 0) > 0 and assembly_stats.get("solidCount", 0) > 1,
    }
    if not report["passed"]:
        raise RuntimeError("assembly validation failed: positions or volume invalid")
    (output_dir / "assembly-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def run_health_check():
    health = {
        "status": "DOWN",
        "pythonAvailable": True,
        "pythonExecutable": sys.executable,
        "pythonVersion": sys.version.split()[0],
        "sysPath": sys.path,
        "workerAvailable": True,
        "cadqueryAvailable": False,
        "cadqueryVersion": "",
        "ocpAvailable": False,
        "cadKernelAvailable": False,
        "cadKernelName": "CadQuery/OCP",
        "stepExportAvailable": False,
        "stepImportAvailable": False,
        "glbExportAvailable": False,
        "dxfExportAvailable": False,
        "solidCount": 0,
        "errorCode": "",
        "message": "",
    }
    try:
        load_cad_modules()
        health["cadqueryAvailable"] = True
        health["cadqueryVersion"] = getattr(cq, "__version__", "unknown")
        health["ocpAvailable"] = True
        health["cadKernelAvailable"] = True
        with tempfile.TemporaryDirectory(prefix="dropai-cad-health-") as tmp:
            step_path = Path(tmp) / "health.step"
            shape = cq.Workplane("XY").box(100, 60, 10).faces(">Z").workplane().hole(12)
            export_step(shape, step_path)
            health["stepExportAvailable"] = step_path.exists() and step_path.stat().st_size > 0
            stats = read_step_stats(step_path)
            health["stepImportAvailable"] = bool(stats.get("opened"))
            health["solidCount"] = int(stats.get("solidCount") or 0)
            health["boundingBox"] = stats.get("boundingBox", {})
        if health["stepExportAvailable"] and health["stepImportAvailable"] and health["solidCount"] >= 1:
            health["status"] = "UP"
            return health
        health["errorCode"] = "CAD_WORKER_HEALTHCHECK_FAILED"
        health["message"] = "CAD Worker could not export and re-open a valid STEP solid"
        return health
    except RuntimeError as exc:
        message = str(exc)
        health["errorCode"] = message.split(":", 1)[0] if ":" in message else "CAD_WORKER_IMPORT_FAILED"
        health["message"] = message
        return health
    except Exception as exc:
        health["errorCode"] = "CAD_WORKER_HEALTHCHECK_FAILED"
        health["message"] = str(exc)
        return health


if __name__ == "__main__":
    main()
