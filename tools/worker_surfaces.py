"""Bake a visible union per animated bone, preserving Minecraft box UVs exactly.

Clothing and body cuboids are authoring solids. Their shared or buried surfaces must
not be submitted twice: at shoulders this caused severe texture flickering. Keep
later outfit surfaces at coincident boundaries, and clip all hidden rectangles.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'src/main/resources/assets/automatons/geometry/worker_surfaces.json'
BONES = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg', 'fishing_chair')


def subtract(rect, cut):
    a, b, c, d = rect
    x, y, xx, yy = max(a, cut[0]), max(b, cut[1]), min(c, cut[2]), min(d, cut[3])
    if x >= xx or y >= yy:
        return [rect]
    return [r for r in [(a,b,x,d), (xx,b,c,d), (x,b,xx,y), (x,yy,xx,d)]
            if r[0] < r[2] and r[1] < r[3]]


def cube_faces(p):
    x,y,z = p['lo']; X,Y,Z = p['hi']; u,v = p['uv']; w,h,d = p['size']
    points = [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
    # Same vertex order, UV orientation and normals as vanilla ModelPart.Cube.
    definitions = [
        ([5,4,0,1], [u+d,v,u+d+w,v+d], (0,-1,0)),
        ([2,3,7,6], [u+d+w,v+d,u+d+w+w,v], (0,1,0)),
        ([0,4,7,3], [u,v+d,u+d,v+d+h], (-1,0,0)),
        ([1,0,3,2], [u+d,v+d,u+d+w,v+d+h], (0,0,-1)),
        ([5,1,2,6], [u+d+w,v+d,u+d+w+d,v+d+h], (1,0,0)),
        ([4,5,6,7], [u+d+w+d,v+d,u+d+w+d+w,v+d+h], (0,0,1))]
    for ids, uv, normal in definitions:
        a,b,c,d = uv
        yield [points[i] for i in ids], [(c,b),(a,b),(a,d),(c,d)], normal


def bake_bone(parts):
    faces = []
    for index,p in enumerate(parts):
        for points,uv,normal in cube_faces(p):
            axis = next(i for i in range(3) if normal[i])
            axes = [i for i in range(3) if i != axis]
            plane = points[0][axis]
            rect = (min(v[axes[0]] for v in points), min(v[axes[1]] for v in points),
                    max(v[axes[0]] for v in points), max(v[axes[1]] for v in points))
            regions = [rect]
            for other_index,other in enumerate(parts):
                if index == other_index:
                    continue
                low, high = other['lo'][axis], other['hi'][axis]
                # A volume beyond the face hides it, including touching solids. At a
                # shared exterior plane only the later (outer clothing) surface wins.
                beyond = low <= plane < high if normal[axis] > 0 else low < plane <= high
                coincident = plane == (high if normal[axis] > 0 else low) and other_index > index
                if beyond or coincident:
                    cut = (other['lo'][axes[0]], other['lo'][axes[1]], other['hi'][axes[0]], other['hi'][axes[1]])
                    regions = [r for region in regions for r in subtract(region,cut)]
            for region in regions:
                verts = []
                for original in points:
                    point = list(original)
                    for k,a in enumerate(axes):
                        point[a] = region[k] if original[a] == rect[k] else region[k+2]
                    # Affine interpolation from original rectangle, retaining UV scale.
                    e1 = [points[1][i]-points[0][i] for i in range(3)]
                    e2 = [points[3][i]-points[0][i] for i in range(3)]
                    delta = [point[i]-points[0][i] for i in range(3)]
                    s = sum(delta[i]*e1[i] for i in range(3))/sum(a*a for a in e1)
                    t = sum(delta[i]*e2[i] for i in range(3))/sum(a*a for a in e2)
                    tex = [uv[0][i]+s*(uv[1][i]-uv[0][i])+t*(uv[3][i]-uv[0][i]) for i in range(2)]
                    verts.extend(point + [round(n/256,8) for n in tex])
                faces.append({'part':p['name'], 'vertices':list(normal)+verts})
    return faces


def build(model):
    parts = model['parts']; lookup = {p['name']:p for p in parts}; variants = []
    for job in range(14):
        prefix = 'guard_' if job == 13 else ''
        groups = {bone:[] for bone in BONES}
        for p in parts:
            name = p['name']
            chair = name.startswith('chair_') or name == 'fishing_chair'
            if chair and job != 9:
                continue
            if not chair and (name.startswith('guard_') != (job == 13) or p['job'] not in (-1,0,job)):
                continue
            parent = p
            offset = list(p['origin'])
            while parent['parent']:
                parent = lookup[parent['parent']]
                offset = [offset[i]+parent['origin'][i] for i in range(3)]
            bone = parent['name'].removeprefix(prefix) if prefix else parent['name']
            if bone in ('neck','shoulder_r','shoulder_l'):
                bone = 'body'
            bone_origin = lookup[prefix+bone if bone != 'fishing_chair' else bone]['origin']
            low = [p['from'][i]+offset[i]-bone_origin[i] for i in range(3)]
            groups[bone].append(dict(p,lo=low,hi=[low[i]+p['size'][i] for i in range(3)]))
        variants.append({bone:bake_bone(solids) for bone,solids in groups.items() if solids})
    return {'variants':variants, 'shoulder_offsets':[4.5,6,4.5,4.5,4.5,4.5,5.5,6,5.5,6,5.5,4.5,6.5,7.5]}


def audit(data, model=None):
    total = 0
    for job,variant in enumerate(data['variants']):
        for bone,faces in variant.items():
            planes = {}
            for face in faces:
                values = face['vertices']; normal = tuple(values[:3]); vertices = [values[i:i+5] for i in range(3,23,5)]
                axis = next(i for i in range(3) if normal[i]); axes = [i for i in range(3) if i != axis]
                rectangle = (min(v[axes[0]] for v in vertices),min(v[axes[1]] for v in vertices),
                             max(v[axes[0]] for v in vertices),max(v[axes[1]] for v in vertices))
                assert rectangle[0] < rectangle[2] and rectangle[1] < rectangle[3]
                key = (axis,vertices[0][axis])
                for old,name in planes.get(key,[]):
                    assert max(old[0],rectangle[0]) >= min(old[2],rectangle[2]) or max(old[1],rectangle[1]) >= min(old[3],rectangle[3]), (job,bone,name,face['part'],'overlapping visible faces')
                planes.setdefault(key,[]).append((rectangle,face['part']))
                for vertex in vertices:
                    assert 0 <= vertex[3] <= 1 and 0 <= vertex[4] <= 1
                total += 1
    print(f'PASS: unassigned and all 13 profession meshes; {total} exposed quads, no overlapping coplanar faces per animated bone; original atlas UVs retained.')
    if model:
        parts = model['parts']; lookup = {p['name']:p for p in parts}; origins = {}
        for p in parts:
            origins[p['name']] = [p['origin'][i]+origins.get(p['parent'],[0,0,0])[i] for i in range(3)]
        for job in range(14):
            boxes = []
            for p in parts:
                if p['name'].startswith(('chair_','fishing_chair')) or p['name'].startswith('guard_') != (job == 13) or p['job'] not in (-1,0,job):
                    continue
                bone = p
                while bone['parent']:
                    bone = lookup[bone['parent']]
                name = bone['name']; origin = origins[p['name']][:]
                if name.endswith('right_arm'): origin[0] += -data['shoulder_offsets'][job]-bone['origin'][0]
                if name.endswith('left_arm'): origin[0] += data['shoulder_offsets'][job]-bone['origin'][0]
                if name in ('neck','shoulder_r','shoulder_l'): name = 'body'
                if name == 'guard_neck': name = 'guard_body'
                low = [origin[i]+p['from'][i] for i in range(3)]; high = [low[i]+p['size'][i] for i in range(3)]
                for other,group,a,b in boxes:
                    assert group == name or any(min(b[i],high[i]) <= max(a[i],low[i]) for i in range(3)), (job,p['name'],other,'intersecting articulated parts')
                boxes.append((p['name'],name,low,high))
        print('PASS: all 14 standing variants have no intersecting solids across articulated body, head, arm and leg groups.')
    for side, suffix in [('right', 'r'), ('left', 'l')]:
        faces = data['variants'][0][side+'_leg']
        assert any(f['part'] == 'boot_'+suffix for f in faces), 'Unassigned worker lost its base boots'
        vertices = [f['vertices'][i:i+3] for f in faces for i in range(3,23,5)]
        assert max(v[0] for v in vertices)-min(v[0] for v in vertices) == 4
        assert max(v[2] for v in vertices)-min(v[2] for v in vertices) == 5
    print('PASS: base boots retained with their full authored silhouette.')


if __name__ == '__main__':
    import sys
    model = json.loads((ROOT/'art/lumberton-model.json').read_text(encoding='utf-8'))
    data = build(model)
    audit(data, model)
    if '--check' in sys.argv:
        assert json.loads(OUTPUT.read_text(encoding='utf-8')) == data, 'Generated worker mesh is stale'
    else:
        OUTPUT.parent.mkdir(parents=True,exist_ok=True)
        OUTPUT.write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
