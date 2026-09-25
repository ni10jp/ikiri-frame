"""Import the separately licensed StarEyes OBJ/MTL and recolor only its eyes.

Download both files from the source linked in README.md, then run:
blender --background --disable-autoexec --python assets/home-emoji/prepare_model.py
Append -- --preview /path/to/preview.png to render the resulting model.
The source assets and generated meshes must not be redistributed in a source repo.
"""
import argparse
import re
import struct
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'assets/home-emoji'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=ROOT / 'StarEyes.obj')
parser.add_argument('--preview', type=Path)
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
source = args.source.resolve()
obj_text = source.read_text()
mtl_name = re.search(r'^mtllib (.+)$', obj_text, re.MULTILINE)
if not mtl_name:
    raise ValueError('The source OBJ must reference its original MTL file.')
mtl_source = source.parent / mtl_name[1].strip()
mtl_text = mtl_source.read_text()
if set(re.findall(r'^newmtl (.+)$', mtl_text, re.MULTILINE)) != {
    'EmojiHead', 'Insides', 'StarEye.001'
}:
    raise ValueError('Unexpected StarEyes material definitions.')

# MTL Kd values and Blender base colors are linear RGB. Change no other property.
red_srgb = (1.0, 29 / 255, 45 / 255)
red = tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in red_srgb)
material_name = None
lines = []
changes = 0
for line in mtl_text.splitlines(keepends=True):
    if line.startswith('newmtl '):
        material_name = line[7:].strip()
    if material_name == 'StarEye.001' and line.startswith('Kd '):
        line = 'Kd ' + ' '.join(f'{v:.9f}' for v in red) + '\n'
        changes += 1
    lines.append(line)
assert changes == 1
red_mtl = ASSETS / 'star-struck-red-eyes.mtl'
red_obj = ASSETS / 'star-struck-red-eyes.obj'
red_mtl.write_text(''.join(lines))
red_obj.write_text(re.sub(r'^mtllib .+$', f'mtllib {red_mtl.name}', obj_text,
                          count=1, flags=re.MULTILINE))

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.wm.obj_import(filepath=str(red_obj))
objects = list(bpy.context.scene.objects)
assert len(objects) == 3 and all(obj.type == 'MESH' for obj in objects)
assert sum(len(obj.data.vertices) for obj in objects) == 29190
assert sum(len(obj.data.polygons) for obj in objects) == 29184

# A single translation and uniform scale align the model to the radius-1 collider.
# Preserve every original vertex, polygon, split normal, and material assignment.
body = bpy.data.objects['BaseFace.016']
corners = [body.matrix_world @ Vector(corner) for corner in body.bound_box]
minimum = Vector(tuple(min(p[i] for p in corners) for i in range(3)))
maximum = Vector(tuple(max(p[i] for p in corners) for i in range(3)))
center = (minimum + maximum) / 2
radius = max(maximum - minimum) / 2
normalize = Matrix.Scale(1 / radius, 4) @ Matrix.Translation(-center)
for obj in objects:
    obj.matrix_world = normalize @ obj.matrix_world
bpy.context.view_layer.update()

# Runtime representation: X right, Y up, Z toward the viewer, linear vertex colors.
triangles = []
for obj in objects:
    mesh = obj.data
    mesh.calc_loop_triangles()
    normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
    for triangle in mesh.loop_triangles:
        color = tuple(mesh.materials[triangle.material_index].diffuse_color[:3])
        for loop_index in triangle.loops:
            position = obj.matrix_world @ mesh.vertices[mesh.loops[loop_index].vertex_index].co
            normal = (normal_matrix @ mesh.corner_normals[loop_index].vector).normalized()
            triangles.extend((position.x, position.z, -position.y,
                              normal.x, normal.z, -normal.y, *color))
output = ROOT / 'app/src/main/assets/models/star-struck.mesh'
output.parent.mkdir(parents=True, exist_ok=True)
with output.open('wb') as file:
    file.write(struct.pack('<II', 0x314d4b49, len(triangles) // 9))
    file.write(struct.pack(f'<{len(triangles)}f', *triangles))
bpy.ops.export_scene.gltf(filepath=str(ASSETS / 'star-struck.glb'), export_format='GLB')
print(f'StarEyes: {len(triangles) // 27} triangles; eye color changed to #FF1D2D; geometry retained.')

if args.preview:
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 32
    scene.cycles.use_denoising = True
    scene.render.resolution_x = scene.render.resolution_y = 800
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'Standard'
    world = bpy.data.worlds.new('Soft studio')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (.19, .21, .24, 1)
    world.node_tree.nodes['Background'].inputs[1].default_value = .6
    scene.world = world
    for name, location, power, size in [('Softbox', (-3, -4, 5), 380, 5),
                                         ('Fill', (4, -2, 1), 110, 4)]:
        light = bpy.data.lights.new(name, 'AREA')
        light.energy, light.shape, light.size = power, 'DISK', size
        obj = bpy.data.objects.new(name, light)
        scene.collection.objects.link(obj)
        obj.location = location
        obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()
    camera = bpy.data.cameras.new('Camera')
    obj = bpy.data.objects.new('Camera', camera)
    scene.collection.objects.link(obj)
    obj.location = (0, -7, 0)
    obj.rotation_euler = (-obj.location).to_track_quat('-Z', 'Y').to_euler()
    camera.type, camera.ortho_scale = 'ORTHO', 2.7
    scene.camera = obj
    args.preview.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(args.preview)
    bpy.ops.render.render(write_still=True)
