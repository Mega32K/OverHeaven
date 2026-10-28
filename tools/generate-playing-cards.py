import argparse
import json
import math
import struct
import zlib
import zipfile
from pathlib import Path


RANKS = ("2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k", "a")
BLOCKS = (
    "dirt", "cobblestone", "copper_block", "iron_block", "lapis_block",
    "gold_block", "emerald_block", "diamond_block", "netherite_block",
)
GLYPHS = {
    "0": ("M", 2.5, 0.5, "Q", 0.5, 0.5, 0.5, 2, "L", 0.5, 5, "Q", 0.5, 6.5, 2.5, 6.5, "Q", 4.5, 6.5, 4.5, 5, "L", 4.5, 2, "Q", 4.5, 0.5, 2.5, 0.5),
    "1": ("M", 1, 1.5, "L", 2.5, 0.5, "L", 2.5, 6.5, "M", 0.8, 6.5, "L", 4.2, 6.5),
    "2": ("M", 0.5, 1.8, "Q", 0.5, 0.5, 2.5, 0.5, "Q", 4.5, 0.5, 4.5, 1.8, "Q", 4.5, 2.8, 3, 3.8, "L", 0.5, 6.5, "L", 4.5, 6.5),
    "3": ("M", 0.5, 0.8, "Q", 4.5, -0.2, 4.5, 2, "Q", 4.5, 3.5, 2.2, 3.5, "Q", 4.5, 3.5, 4.5, 5, "Q", 4.5, 7.2, 0.5, 6.2),
    "4": ("M", 3.6, 6.5, "L", 3.6, 0.5, "L", 0.5, 4.5, "L", 4.5, 4.5),
    "5": ("M", 4.5, 0.5, "L", 0.5, 0.5, "L", 0.5, 3.3, "Q", 4.5, 2.5, 4.5, 4.8, "Q", 4.5, 7.2, 0.5, 6.1),
    "6": ("M", 4.2, 0.7, "Q", 0.5, -0.2, 0.5, 4.8, "Q", 0.5, 6.5, 2.5, 6.5, "Q", 4.5, 6.5, 4.5, 4.8, "Q", 4.5, 2.4, 0.5, 3.6),
    "7": ("M", 0.5, 0.5, "L", 4.5, 0.5, "L", 1.6, 6.5),
    "8": ("M", 2.5, 3.4, "Q", 0.5, 3.2, 0.5, 1.9, "Q", 0.5, 0.5, 2.5, 0.5, "Q", 4.5, 0.5, 4.5, 1.9, "Q", 4.5, 3.2, 2.5, 3.4, "Q", 0.5, 3.7, 0.5, 5, "Q", 0.5, 6.5, 2.5, 6.5, "Q", 4.5, 6.5, 4.5, 5, "Q", 4.5, 3.7, 2.5, 3.4),
    "9": ("M", 0.8, 6.3, "Q", 4.5, 7.2, 4.5, 2.2, "Q", 4.5, 0.5, 2.5, 0.5, "Q", 0.5, 0.5, 0.5, 2.2, "Q", 0.5, 4.6, 4.5, 3.4),
    "J": ("M", 1.5, 0.5, "L", 4.5, 0.5, "M", 3.5, 0.5, "L", 3.5, 5, "Q", 3.5, 6.5, 2, 6.5, "Q", 0.5, 6.5, 0.5, 5),
    "Q": ("M", 2.5, 0.5, "Q", 0.5, 0.5, 0.5, 2, "L", 0.5, 5, "Q", 0.5, 6.5, 2.5, 6.5, "Q", 4.5, 6.5, 4.5, 5, "L", 4.5, 2, "Q", 4.5, 0.5, 2.5, 0.5, "M", 2.8, 4.8, "L", 4.7, 6.7),
    "K": ("M", 0.5, 0.5, "L", 0.5, 6.5, "M", 4.5, 0.5, "L", 0.5, 4, "M", 2, 2.7, "L", 4.5, 6.5),
    "A": ("M", 0.5, 6.5, "L", 2.5, 0.5, "L", 4.5, 6.5, "M", 1.2, 4.5, "L", 3.8, 4.5),
}
PAPER = (242, 235, 218, 255)
INK = (37, 43, 53, 255)
GOLD = (167, 137, 89, 255)
NAVY = (29, 43, 62, 255)
RANK_SCALE = 2
CARD_SCALE = 4
ICON_SAMPLES = 2
ICON_SCALE = 1.5
CAMERA_RIGHT = (1 / math.sqrt(2), 0, -1 / math.sqrt(2))
CAMERA_UP = (-1 / math.sqrt(6), 2 / math.sqrt(6), -1 / math.sqrt(6))
CAMERA_FORWARD = (1 / math.sqrt(3),) * 3


def paeth(left, above, upper_left):
    prediction = left + above - upper_left
    distances = (abs(prediction - left), abs(prediction - above), abs(prediction - upper_left))
    return (left, above, upper_left)[distances.index(min(distances))]


def read_png(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Input is not a PNG")
    offset = 8
    compressed = bytearray()
    width = height = None
    palette = b""
    transparency = b""
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        expected = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != expected:
            raise ValueError("Invalid PNG checksum")
        offset += 12 + length
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            if depth != 8 or color not in (3, 6) or (compression, filtering, interlace) != (0, 0, 0):
                raise ValueError("Expected non-interlaced 8-bit RGBA or indexed PNG")
        elif kind == b"PLTE":
            palette = payload
        elif kind == b"tRNS":
            transparency = payload
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
    if width is None or height is None:
        raise ValueError("PNG has no dimensions")
    raw = zlib.decompress(compressed)
    channels = 4 if color == 6 else 1
    stride = width * channels
    if len(raw) != (stride + 1) * height:
        raise ValueError("Unexpected PNG data size")
    result = Image(width, height)
    previous = bytearray(stride)
    for row_index in range(height):
        start = row_index * (stride + 1)
        filter_type = raw[start]
        filtered = raw[start + 1:start + 1 + stride]
        row = bytearray(stride)
        for column in range(stride):
            left = row[column - channels] if column >= channels else 0
            above = previous[column]
            upper_left = previous[column - channels] if column >= channels else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = above
            elif filter_type == 3:
                predictor = (left + above) // 2
            elif filter_type == 4:
                predictor = paeth(left, above, upper_left)
            else:
                raise ValueError("Unsupported PNG filter")
            row[column] = (filtered[column] + predictor) & 255
        if color == 6:
            result.pixels[row_index * stride:(row_index + 1) * stride] = row
        else:
            for column, index in enumerate(row):
                red, green, blue = palette[index * 3:index * 3 + 3]
                alpha = transparency[index] if index < len(transparency) else 255
                result.put(column, row_index, (red, green, blue, alpha))
        previous = row
    return result


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)


class Image:
    def __init__(self, width, height, color=(0, 0, 0, 0)):
        self.width = width
        self.height = height
        self.pixels = bytearray(color * (width * height))

    def get(self, x, y):
        offset = (y * self.width + x) * 4
        return tuple(self.pixels[offset:offset + 4])

    def put(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            offset = (y * self.width + x) * 4
            self.pixels[offset:offset + 4] = bytes(color)

    def rectangle(self, left, top, right, bottom, color):
        for y in range(top, bottom):
            for x in range(left, right):
                self.put(x, y, color)

    def line(self, start, end, color):
        x, y = start
        target_x, target_y = end
        delta_x, delta_y = abs(target_x - x), abs(target_y - y)
        step_x = 1 if x < target_x else -1
        step_y = 1 if y < target_y else -1
        error = delta_x - delta_y
        while True:
            self.put(x, y, color)
            if (x, y) == (target_x, target_y):
                break
            doubled = error * 2
            if doubled > -delta_y:
                error -= delta_y
                x += step_x
            if doubled < delta_x:
                error += delta_x
                y += step_y

    def paste_scaled(self, source, source_box, left, top, scale):
        source_x, source_y, width, height = source_box
        for y in range(height):
            for x in range(width):
                color = source.get(source_x + x, source_y + y)
                if color[3] == 0:
                    continue
                for delta_y in range(scale):
                    for delta_x in range(scale):
                        dest_x, dest_y = left + x * scale + delta_x, top + y * scale + delta_y
                        if color[3] == 255:
                            self.put(dest_x, dest_y, color)
                        else:
                            background = self.get(dest_x, dest_y)
                            alpha = color[3]
                            mixed = tuple((color[index] * alpha + background[index] * (255 - alpha)) // 255 for index in range(3))
                            self.put(dest_x, dest_y, mixed + (255,))

    def save(self, path):
        header = struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0)
        stride = self.width * 4
        raw = b"".join(b"\x00" + self.pixels[row * stride:(row + 1) * stride] for row in range(self.height))
        data = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header) + png_chunk(b"IDAT", zlib.compress(raw, 9)) + png_chunk(b"IEND", b"")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def outline(image, left, top, right, bottom, color):
    image.line((left, top), (right, top), color)
    image.line((right, top), (right, bottom), color)
    image.line((right, bottom), (left, bottom), color)
    image.line((left, bottom), (left, top), color)


def diamond(image, center_x, center_y, radius, color):
    for y in range(-radius, radius + 1):
        width = radius - abs(y)
        image.rectangle(center_x - width, center_y + y, center_x + width + 1, center_y + y + 1, color)


def glyph_segments(character):
    commands = iter(GLYPHS[character])
    segments = []
    current = None
    for command in commands:
        if command == "M":
            current = (next(commands), next(commands))
        elif command == "L":
            endpoint = (next(commands), next(commands))
            segments.append((current, endpoint))
            current = endpoint
        elif command == "Q":
            control = (next(commands), next(commands))
            endpoint = (next(commands), next(commands))
            start = current
            for step in range(1, 25):
                amount = step / 24
                point = tuple((1 - amount) ** 2 * start[axis] + 2 * amount * (1 - amount) * control[axis] + amount ** 2 * endpoint[axis] for axis in range(2))
                segments.append((current, point))
                current = point
        else:
            raise ValueError("Unknown glyph command: " + command)
    return segments


def draw_rank(image, rank):
    pixel_scale = image.width / 64
    for character_index, character in enumerate(rank.upper()):
        segments = glyph_segments(character)
        for row in range(round(7.5 * RANK_SCALE * pixel_scale)):
            for column in range(round(5.5 * RANK_SCALE * pixel_scale)):
                sample = ((column + 0.5) / (RANK_SCALE * pixel_scale), (row + 0.5) / (RANK_SCALE * pixel_scale))
                minimum_distance = math.inf
                for start, end in segments:
                    delta = (end[0] - start[0], end[1] - start[1])
                    length_squared = delta[0] ** 2 + delta[1] ** 2
                    amount = max(0, min(1, ((sample[0] - start[0]) * delta[0] + (sample[1] - start[1]) * delta[1]) / length_squared))
                    distance = math.hypot(sample[0] - start[0] - amount * delta[0], sample[1] - start[1] - amount * delta[1])
                    minimum_distance = min(minimum_distance, distance)
                coverage = max(0, min(1, (0.36 - minimum_distance) * RANK_SCALE * pixel_scale + 0.5))
                if coverage:
                    pixel_x = round((8 + character_index * 6 * RANK_SCALE) * pixel_scale) + column
                    pixel_y = round(9 * pixel_scale) + row
                    background = image.get(pixel_x, pixel_y)
                    color = tuple(round(INK[channel] * coverage + background[channel] * (1 - coverage)) for channel in range(3)) + (255,)
                    image.put(pixel_x, pixel_y, color)
                    image.put(image.width - 1 - pixel_x, image.height - 1 - pixel_y, color)


def upscale(image, scale):
    result = Image(image.width * scale, image.height * scale)
    result.paste_scaled(image, (0, 0, image.width, image.height), 0, 0, scale)
    return result


def card_face(rank):
    image = Image(64, 96, PAPER)
    image.rectangle(0, 0, 64, 1, (225, 215, 193, 255))
    image.rectangle(0, 95, 64, 96, (216, 204, 179, 255))
    outline(image, 1, 1, 62, 94, INK)
    outline(image, 4, 4, 59, 91, GOLD)
    diamond(image, 52, 14, 2, GOLD)
    diamond(image, 11, 81, 2, GOLD)
    image = upscale(image, CARD_SCALE)
    draw_rank(image, rank)
    return image


def shade(color, factor):
    return tuple(min(255, round(channel * factor)) for channel in color[:3]) + (255,)


def sample_model(screen_right, screen_up, texture, surfaces):
    origin = tuple(
        8 + CAMERA_RIGHT[axis] * screen_right + CAMERA_UP[axis] * screen_up
        + CAMERA_FORWARD[axis] * 64 for axis in range(3)
    )
    closest_distance = math.inf
    closest_color = None
    for face_name, normal_axis, brightness, lower, upper, uv in surfaces:
        distance = (origin[normal_axis] - upper[normal_axis]) / CAMERA_FORWARD[normal_axis]
        if distance < 0 or distance >= closest_distance:
            continue
        point = tuple(origin[axis] - distance * CAMERA_FORWARD[axis] for axis in range(3))
        if any(point[axis] < lower[axis] - 1e-8 or point[axis] > upper[axis] + 1e-8 for axis in range(3)):
            continue
        local = tuple((point[axis] - lower[axis]) / (upper[axis] - lower[axis]) for axis in range(3))
        if face_name == "up":
            horizontal, vertical = local[0], local[2]
        elif face_name == "south":
            horizontal, vertical = local[0], 1 - local[1]
        else:
            horizontal, vertical = 1 - local[2], 1 - local[1]
        uv_left, uv_top, uv_right, uv_bottom = uv
        texture_x = min(texture.width - 1, max(0, math.floor((uv_left + horizontal * (uv_right - uv_left)) * texture.width / 16)))
        texture_y = min(texture.height - 1, max(0, math.floor((uv_top + vertical * (uv_bottom - uv_top)) * texture.height / 16)))
        closest_distance = distance
        closest_color = shade(texture.get(texture_x, texture_y), brightness)
    return closest_color


def draw_model(image, texture, elements):
    visible_faces = (("up", 1, 1.0), ("south", 2, 0.86), ("east", 0, 0.68))
    surfaces = []
    for element in elements:
        if "rotation" in element:
            raise ValueError("Rotated model elements are not supported by this card renderer")
        for face_name, normal_axis, brightness in visible_faces:
            face = element["faces"].get(face_name)
            if face is not None:
                if face.get("rotation", 0) != 0:
                    raise ValueError("Rotated face UVs are not supported by this card renderer")
                surfaces.append((face_name, normal_axis, brightness, element["from"], element["to"], face["uv"]))
    pixel_scale = image.width / 64
    for pixel_y in range(round(25 * pixel_scale), round(72 * pixel_scale)):
        for pixel_x in range(round(9 * pixel_scale), round(55 * pixel_scale)):
            background = image.get(pixel_x, pixel_y)
            samples = []
            for sample_y in range(ICON_SAMPLES):
                for sample_x in range(ICON_SAMPLES):
                    logical_x = (pixel_x + (sample_x + 0.5) / ICON_SAMPLES) / pixel_scale
                    logical_y = (pixel_y + (sample_y + 0.5) / ICON_SAMPLES) / pixel_scale
                    color = sample_model((logical_x - 32) / ICON_SCALE, (48 - logical_y) / ICON_SCALE, texture, surfaces)
                    samples.append(color if color is not None else background)
            image.put(pixel_x, pixel_y, tuple(round(sum(color[channel] for color in samples) / len(samples)) for channel in range(4)))


def draw_cube(image, texture):
    draw_model(image, texture, [{
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {face: {"uv": [0, 0, 16, 16]} for face in ("up", "south", "east")},
    }])


def draw_piglin(image, texture):
    image.rectangle(8, 38, 18, 49, INK)
    image.rectangle(46, 38, 56, 49, INK)
    image.paste_scaled(texture, (39, 10, 4, 5), 9, 38, 2)
    image.paste_scaled(texture, (39, 10, 4, 5), 47, 38, 2)
    image.rectangle(16, 33, 48, 59, INK)
    image.paste_scaled(texture, (8, 8, 10, 8), 17, 34, 3)
    image.paste_scaled(texture, (32, 2, 4, 4), 26, 46, 3)
    image.paste_scaled(texture, (3, 5, 1, 2), 23, 52, 3)
    image.paste_scaled(texture, (3, 1, 1, 2), 38, 52, 3)


def draw_head(image, texture, eyes=None):
    image.rectangle(15, 31, 49, 65, INK)
    image.paste_scaled(texture, (8, 8, 8, 8), 16, 32, 4)
    if eyes is not None:
        image.paste_scaled(eyes, (8, 8, 8, 8), 16, 32, 4)


def card_back():
    image = Image(64, 96, NAVY)
    outline(image, 1, 1, 62, 94, (225, 215, 193, 255))
    outline(image, 3, 3, 60, 92, GOLD)
    outline(image, 6, 6, 57, 89, (96, 118, 145, 255))
    for y in range(8, 88):
        for x in range(8, 56):
            mirrored_x = min(x, 63 - x)
            mirrored_y = min(y, 95 - y)
            if (mirrored_x + mirrored_y) % 12 == 0 or (mirrored_x - mirrored_y) % 12 == 0:
                image.put(x, y, (45, 65, 88, 255))
    for radius, color in ((17, GOLD), (15, NAVY), (12, (109, 128, 150, 255)), (10, NAVY)):
        centered_back_diamond(image, radius, color)
    centered_back_diamond(image, 5, GOLD)
    centered_back_diamond(image, 3, NAVY)
    for x, y in ((12, 12), (51, 12), (12, 83), (51, 83)):
        diamond(image, x, y, 2, GOLD)
    return image


def centered_back_diamond(image, radius, color):
    for y in range(96):
        for x in range(64):
            if abs(2 * x - 63) + abs(2 * y - 95) <= 2 * radius:
                image.put(x, y, color)


def pad_card(image):
    padded = Image(128 * CARD_SCALE, 128 * CARD_SCALE)
    for y in range(padded.height):
        for x in range(padded.width):
            padded.put(x, y, image.get(min(x, image.width - 1), min(y, image.height - 1)))
    return padded


def preview(cards, destination):
    card_width, card_height = cards[0].width, cards[0].height
    column_width, row_height = card_width + 24, card_height + 24
    image = Image(7 * column_width + 24, 2 * row_height + 24, (46, 55, 67, 255))
    for index, card in enumerate(cards):
        left = 24 + (index % 7) * column_width
        top = 12 + (index // 7) * row_height
        image.rectangle(left + 5, top + 5, left + card_width + 5, top + card_height + 5, (30, 36, 45, 255))
        image.paste_scaled(card, (0, 0, card_width, card_height), left, top, 1)
    image.save(destination)


def main():
    parser = argparse.ArgumentParser(description="Generate Minecraft playing-card textures from a local 1.20.1 client JAR")
    parser.add_argument("--minecraft-jar", type=Path, required=True)
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args()
    output = Path(__file__).resolve().parents[1] / "src/main/resources/assets/overheaven_foliage_compat/textures/item/cards"
    with zipfile.ZipFile(args.minecraft_jar) as archive:
        def texture(path):
            return read_png(archive.read("assets/minecraft/textures/" + path + ".png"))

        blocks = [texture("block/" + name) for name in BLOCKS]
        piglin = texture("entity/piglin/piglin")
        enderman = texture("entity/enderman/enderman")
        enderman_eyes = texture("entity/enderman/enderman_eyes")
        wither_skeleton = texture("entity/skeleton/wither_skeleton")
        dragon_egg = texture("block/dragon_egg")
        dragon_egg_model = json.loads(archive.read("assets/minecraft/models/block/dragon_egg.json"))
    cards = []
    for index, rank in enumerate(RANKS):
        image = card_face(rank)
        if index < len(blocks):
            draw_cube(image, blocks[index])
        elif rank in ("j", "q", "k"):
            portrait = Image(64, 96)
            if rank == "j":
                draw_piglin(portrait, piglin)
            elif rank == "q":
                draw_head(portrait, enderman, enderman_eyes)
            else:
                draw_head(portrait, wither_skeleton)
            image.paste_scaled(portrait, (0, 0, 64, 96), 0, 0, CARD_SCALE)
        else:
            draw_model(image, dragon_egg, dragon_egg_model["elements"])
        pad_card(image).save(output / ("playing_card_" + rank + ".png"))
        cards.append(image)
    back = upscale(card_back(), CARD_SCALE)
    pad_card(back).save(output / "playing_card_back.png")
    edge = Image(16, 16, (224, 214, 191, 255))
    edge.rectangle(0, 3, 16, 5, (245, 236, 218, 255))
    edge.rectangle(0, 10, 16, 12, (194, 179, 151, 255))
    edge.save(output / "playing_card_edge.png")
    if args.preview:
        preview(cards + [back], args.preview)
    print("Generated 13 card faces, one back and one edge from", args.minecraft_jar)
    print("Texture directory:", output)
    if args.preview:
        print("Preview:", args.preview)


if __name__ == "__main__":
    main()
