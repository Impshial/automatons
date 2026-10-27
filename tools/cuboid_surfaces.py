"""Subtract hidden/coplananr patches while retaining native texture coordinates.

Only parts sharing a motion group can occlude each other. Later decorative parts
own a shared exterior plane, so iron trim cannot fight a wooden panel beneath it.
"""

def subtract(rect, cut):
    a, b, c, d = rect
    x, y, z, w = max(a, cut[0]), max(b, cut[1]), min(c, cut[2]), min(d, cut[3])
    if x >= z - 1e-7 or y >= w - 1e-7:
        return [rect]
    return [r for r in [(a,b,x,d), (z,b,c,d), (x,b,z,y), (x,w,z,d)]
            if r[2] - r[0] > 1e-7 and r[3] - r[1] > 1e-7]

def surfaces(boxes):
    for i, box in enumerate(boxes):
        faces = []
        for axis in range(3):
            u, v = (axis + 1) % 3, (axis + 2) % 3
            for sign in [-1, 1]:
                plane = box['from' if sign < 0 else 'to'][axis]
                rects = [(box['from'][u], box['from'][v], box['to'][u], box['to'][v])]
                for j, other in enumerate(boxes):
                    if i == j or (box.get('group'), box.get('pivot')) != (other.get('group'), other.get('pivot')):
                        continue
                    lo, hi = other['from'][axis], other['to'][axis]
                    if sign > 0:
                        covered = lo <= plane + 1e-7 and (hi > plane + 1e-7 or abs(hi-plane) < 1e-7 and j > i)
                    else:
                        covered = hi >= plane - 1e-7 and (lo < plane - 1e-7 or abs(lo-plane) < 1e-7 and j > i)
                    if covered:
                        cut = [other['from'][u], other['from'][v], other['to'][u], other['to'][v]]
                        rects = [piece for r in rects for piece in subtract(r, cut)]
                faces += [{'axis':axis, 'sign':sign, 'rect':list(r)} for r in rects]
        box['faces'] = faces
    return boxes
