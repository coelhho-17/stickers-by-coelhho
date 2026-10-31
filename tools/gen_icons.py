# -*- coding: utf-8 -*-
"""Gera os ícones do app (adaptativo + fallback legacy) a partir da imagem do usuário."""
import os
import sys
from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else "icon_source.jpeg"
# res/ fica na raiz do repo (tools/../res)
RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "res")

WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

# Fator de escala: a imagem ocupa FILL da largura do canvas, centralizada.
# 0.62 ~ safe zone do ícone adaptativo (66dp de 108dp), garante que o desenho
# inteiro fique visível dentro da máscara arredondada do launcher.
FILL = 0.62

# densidades Android (multiplicador de dp -> px)
DENS = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# ícone legacy (launcher) = 48dp; foreground adaptativo = 108dp
LEGACY_DP = 48
FG_DP = 108


def contain_fit(img, size, bg):
    """Cria canvas quadrado size x size, cola img com 'contain' (mantém proporção) centralizada."""
    canvas = Image.new("RGBA", (size, size), bg)
    ratio = min(size / img.width, size / img.height) * FILL
    nw = max(1, int(img.width * ratio))
    nh = max(1, int(img.height * ratio))
    resized = img.resize((nw, nh), Image.LANCZOS)
    x = (size - nw) // 2
    y = (size - nh) // 2
    canvas.paste(resized, (x, y))  # RGB opaco sobre o fundo
    return canvas


def main():
    src = Image.open(SRC).convert("RGB")
    print(f"fonte: {src.size} {src.mode}")

    for name, scale in DENS.items():
        # legacy (fundo branco, imagem inteira visível)
        ls = int(LEGACY_DP * scale)
        legacy = contain_fit(src, ls, WHITE)
        ld = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(ld, exist_ok=True)
        legacy.save(os.path.join(ld, "ic_launcher.png"))
        print(f"legacy {name}: {ls}x{ls}")

        # foreground adaptativo (fundo transparente)
        fs = int(FG_DP * scale)
        fg = contain_fit(src, fs, TRANSPARENT)
        fd = os.path.join(RES, f"mipmap-{name}")
        fg.save(os.path.join(fd, "ic_launcher_foreground.png"))
        print(f"foreground {name}: {fs}x{fs}")

    print("OK - ícones gerados")


if __name__ == "__main__":
    main()
