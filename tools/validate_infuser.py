"""Detect coplanar face overlap on the infuser; never edits models or textures."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def validate(model):
    faces = []
    for index, element in enumerate(model['elements']):
        start, end = element['from'], element['to']
        for face in element['faces']:
            axis = {'west': 0, 'east': 0, 'down': 1, 'up': 1, 'north': 2, 'south': 2}[face]
            plane = (end if face in ('east', 'up', 'south') else start)[axis]
            dimensions = [i for i in range(3) if i != axis]
            for old_index, old_face, old_plane, old_start, old_end in faces:
                if face == old_face and plane == old_plane:
                    assert not all(min(end[i], old_end[i]) > max(start[i], old_start[i])
                                   for i in dimensions), f'Overlapping {face} faces: {old_index}, {index}'
            faces.append((index, face, plane, start, end))
    top_area = sum((end[0]-start[0])*(end[2]-start[2])
                   for _, face, plane, start, end in faces if face == 'up' and plane == 16)
    assert top_area == 4 * 16, f'Incomplete socket tops: {top_area}'
    return {'coplanar_overlaps': 0, 'socket_top_area': top_area, 'faces': len(faces)}


if __name__ == '__main__':
    model = json.loads((ROOT/'src/main/resources/assets/automatons/models/block/infusing_pedestal.json').read_text(encoding='utf-8'))
    print(json.dumps(validate(model), indent=2))
