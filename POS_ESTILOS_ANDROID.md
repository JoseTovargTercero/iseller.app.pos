# iSeller POS — Sistema de estilos y guía de port a Android

> Documento de referencia de diseño extraído del **POS web** de iSeller para reimplementar la
> interfaz en la **app Android** (versión Android iSeller).
> Todos los valores son los reales del código, no aproximaciones.

> **Override app Android (iSeller POS):** el color **primario/acento es `#049FA5`** (variante oscura
> y fin del gradiente de marca `#038086`), en lugar del menta `#2DD4A0`. Definido en
> `app/src/main/res/values/colors.xml` como `pos_primary` / `pos_primary_dark`. El resto de tokens
> (fondos `#181B22`/`#1A1E24`, borde `#2E353E`, texto `#E8EDF2`/`#8892A0`, semánticos, radios y
> tipografía) se mantiene igual.

---

## 0. Fuentes analizadas

| Archivo | Qué contiene | Rol |
|---|---|---|
| `publico/production/ventas.php` | POS. 5 bloques `<style>` (líneas 324, 1266, 1494, 2383, 2673) + markup | **Fuente principal** |
| `publico/production/theme.css` | Tema "Falcon" (Bootstrap 5, tokens `--falcon-*`) | Tema base heredado |
| `publico/build/css/global-styles.css` | Tokens `--dash-*`, menú lateral, top-nav, notificaciones, banners | Sistema propio iSeller |
| `publico/build/css/custom.min.dark-mode.css` | Dark theme global: layout, `x_panel`, tablas, `form-control`, `modal-container` | Overrides dark |
| `publico/assets/templates/modal.html` | Plantilla del modal de búsqueda de productos (`#modal-container`) | Componente POS |

**Contexto técnico:** el POS web carga a la vez Bootstrap 4 (vendors) y Bootstrap 5 (CDN),
Falcon, y varias librerías de iconos. Para Android **no se replica esa deuda**: se toma solo
la capa de diseño `--dash-*` (la que realmente define el look del POS) y se mapea a Material 3.

---

## 1. Principios visuales

1. **Dark UI de alto contraste** sobre superficies casi negras azuladas (`#181b22` / `#1a1e24`).
2. **Verde menta (`#2dd4a0`) es el acento único** de marca: acciones principales, foco, activo,
   éxito, badges positivos. Todo lo demás es neutro.
3. **Semánticos saturados** solo para estado: info azul, warning ámbar, danger rojo.
4. **Bordes finos de 1px** (`#2e353e`) + **superficies translúcidas blancas** (2–8%) para elevar
   sin sombras fuertes. Sombra reservada a flotantes (modales, dropdowns, botón primario).
5. **Radios medios** (6–14px), nunca esquinas vivas; pills (50px) para avisos.
6. **Densidad de escritorio**, ahora a adaptar a táctil (ver §10).

---

## 2. Tokens

### 2.1 Colores

#### Base / superficies
| Token | Hex | Uso |
|---|---|---|
| `--dash-bg` | `#1a1e24` | Fondo de campos/inputs, `input-group-text`, tablas, "elevated". *(En `global-styles.css` vale `transparent`; el POS lo redefine a `#1a1e24`.)* |
| `--dash-card` | `#181b22` | Superficie principal: `right_col`, sidebar `#navbar`, dropdowns, modales, cards |
| `--dash-border` | `#2e353e` | Todos los bordes, divisores, track de barra |
| `--dash-mint-dim` | `rgba(45,212,160,0.12)` | Fondo tenue de acento |
| `--dash-mint-glow` | `rgba(45,212,160,0.06)` | Hover tenue |
| White 2% | `rgba(255,255,255,0.02)` | Fila impar de tabla (zebra) |
| White 3% | `rgba(255,255,255,0.03)` | Hover de fila / fondo de ítem |
| White 4% | `rgba(255,255,255,0.04)` | Hover neutro / fondo de miniatura |
| White 6% | `rgba(255,255,255,0.06)` | Botón "dark"/neutro, avatar de menú |
| White 8% | `rgba(255,255,255,0.08)` | Hover de botón neutro / toggle |

#### Marca y semánticos
| Token | Hex | Uso |
|---|---|---|
| `--dash-mint` | `#2dd4a0` | **Primario/éxito/activo** |
| mint hover | `#25b88a` | Extremo del gradiente primario |
| `--dash-info` | `#5b9cf5` | Información, precios en pesos/COP |
| `--dash-danger` | `#ef5a6f` | Peligro, precios en Bs, eliminar |
| `--dash-warning` | `#f5b45b` | Advertencia, fondo de caja, pedidos web |
| success legacy | `#2fb344` | Fallback de `--dash-success` |
| `--dash-text` | `#e8edf2` | Texto principal |
| `--dash-text-muted` | `#8892a0` | Texto secundario, labels, placeholders |
| sidebar hover (teal) | `#1ABB9C` | Hover de ítem de menú lateral |
| notif stock | `#fbbf24` | Icono aviso de stock |
| notif sistema | `#60a5fa` | Icono aviso de sistema |
| notif chat | `#1a7f4b` | Icono aviso de chat |

#### Overlays
| Token | Valor | Uso |
|---|---|---|
| Overlay modal custom | `rgba(0,0,0,0.70)` | `.details-modal-overlay` |
| Overlay scanner | `rgba(0,0,0,0.55)` | `#section-scanner` |
| Overlay menú móvil | `rgba(0,0,0,0.50)` | `.mobile-menu-overlay` |

#### Gradientes
```css
/* Botón primario, botón vender, avatar, scan-btn.add */
background: linear-gradient(135deg, #2dd4a0, #25b88a);
/* Scrollbar global (decorativo) */
background: repeating-linear-gradient(top left, #52d3aa 0%, #3f95ea 600%);
```

### 2.2 Tipografía
| Rol | Familia | Tamaño | Peso | Extra |
|---|---|---|---|---|
| **Body / POS** | `"Helvetica Neue", Roboto, Arial, "Droid Sans", sans-serif` | **13px** | 400 | line-height 1.471 |
| Marca "iSeller" | `'Sora','Poppins',sans-serif` | 1.4rem | 800 | letter-spacing -.02em |
| Título de panel (`x_title h2`) | heredada | **15px** | 600 | — |
| Encabezado de tabla | heredada | 12px | 600 | UPPERCASE, ls .3–.4px, color muted |
| Labels de modal | heredada | 13px | 600 | color muted |
| Label de cierre (`cierre-label`) | heredada | 0.7rem | 600 | ls .3px |
| Badges | heredada | 0.65–11px | 600–700 | — |
| Botones | heredada | 12–13px | 600–700 | UPPERCASE + ls .3–.5px en acciones |
| Ticket | Arial | **11px** | — | ancho 44mm, centrado |

> **Para Android:** usar **Roboto** (incluida en el sistema) con los mismos tamaños en `sp`.
> Alternativa de marca para títulos: **Poppins/Sora** (requiere empaquetar la fuente).

### 2.3 Radios
| Valor | Aplica a |
|---|---|
| `6px` | Badges |
| `7px` | `x_panel` (panel heredado) |
| `8px` | Inputs, selects, botones, `input-td`, `scan-quantity`, avatares cuadrados |
| `10px` | `section-card`, dropdowns, miniatura de imagen, `pos-top-item` |
| `12px` | `caja-cerrada-container` (y dropdowns top-nav) |
| `14px` | `dash-panel`, `modal-content`, modal nativo de impresión |
| `8px 0 0 8px` / `0 8px 8px 0` | Extremos de `btn-group` |
| `50%` | Avatares, dots, spinner |
| `50px` / `100px` | Pills de aviso (banners, badge del landing) |

### 2.4 Sombras
| Token | Valor | Uso |
|---|---|---|
| Botón primario | `0 3px 12px rgba(45,212,160,0.25)` | reposo |
| Botón primario hover | `0 6px 20px rgba(45,212,160,0.35)` | hover / `#btn-vender` hover `0 6px 20px …0.40` |
| Botón success | `0 2px 8px rgba(45,212,160,0.20)` | reposo |
| Dropdown | `0 8px 24px rgba(0,0,0,0.30)` | menús |
| Modal nativo impresión | `0 8px 32px rgba(0,0,0,0.40)` | diálogo |
| Modal custom | `0 10px 30px rgba(0,0,0,0.30)` | `.details-modal` |
| Panel `x_panel` | `0 0 9px 0 rgba(0,0,0,0.01)` | casi plano |
| Caja cerrada | `0 4px 15px rgba(0,0,0,0.20)` | card central |
| Focus ring input | `0 0 0 2px rgba(45,212,160,0.15)` | `form-control:focus` |

### 2.5 Espaciado
Base **4px**. Valores observados:
`3 · 5 · 6 · 8 · 10 · 12 · 14 · 15 · 16 · 18 · 20 · 24 · 28` px.
- `.right_col` padding `24px 28px` (desktop) / `10px` (≤991px).
- `.x_panel` padding `10px 17px`.
- `.section-card` padding `15px`; `modal-body` padding `24px`.
- Gaps típicos: `8px` (botones en fila), `10px` (grid de acciones), `12–14px` (grids de cards).

### 2.6 Bordes
- Ancho: `1px` estándar; `2px` en relleno de foco y borde inferior de tab activo.
- `border-left: 4px solid` como **código de color** en `section-card`: mint (efectivo), info (digital), `#f5b45b` (fondo).
- `border-dashed` en `item-reservado` y en el separador del ticket (`.line`).

### 2.7 z-index (escala real)
| Capa | z-index |
|---|---|
| `pedidos-web-alert` | 20 |
| `#section-scanner` | 999 |
| `mobile-menu-overlay` | 9997 |
| `#navbar` / `.modal-container` | 9999 |
| `modal-backdrop` | 10040 |
| `.modal` (Bootstrap) | 10045 |
| `#modalImagen` | 10050 |
| SweetAlert2 / `contenedor-loader` / notif-panel | 99999 |

### 2.8 Breakpoints
`576px · 768px · 992px · 1200px · 1270px · 1456px`
Puntos clave del POS: **≤992px** (menú lateral pasa a overlay y aparece hamburguesa),
**≤768px** (se oculta el banner de membresía), **≤576px** (grillas de acciones a 1 columna).

---

## 3. Layout

```
┌──────────┬────────────────────────────────────────────┐
│ #navbar  │  top-nav (topnav)                          │
│ 5em /    ├────────────────────────────────────────────┤
│ 16em     │  .right_col  (#181b22, padding 24/28)      │
│ fijo     │   └ x_panel / dash-panel (contenido)       │
└──────────┴────────────────────────────────────────────┘
```

- **Sidebar `#navbar`:** ancho colapsado `5em`, expandido `16em` en `:hover` (desktop) o `.open` (móvil).
  `position: fixed; height:100vh; background #181b22; border-right 1px #2e353e; width transition .35s`.
  En ≤992px: `width:16em; transform:translateX(-100%)` y entra con `.open`.
- **Ítem de menú `.navbar-item-inner`:** `padding:1em 0; border-radius:.25em; transition .2s`.
  Hover/activo: `background:#1ABB9C; color:#fff; box-shadow:0 17px 30px -10px rgba(0,0,0,.25)`.
  Icono centrado en un wrapper de `calc(5rem-1em-8px)`; el texto `.link-text` aparece solo al expandir.
- **Top-nav:** barra con título de página (`page-title`, `margin-left:82px` en desktop), campana de
  notificaciones, banner de membresía centrado (50%) y dropdown de usuario a la derecha.
- **Contenido:** `right_col` con grid de Bootstrap; en el POS la vista principal es 1 panel a ancho
  completo con tabs (`Carrito activo / No guardados / Reservados / Enviados`).

---

## 4. Componentes

### 4.1 Botones (`ventas.php` + `global-styles.css`)
| Clase | Fondo | Texto | Radio | Pad | Notas |
|---|---|---|---|---|---|
| `.btn-primary` | `linear-gradient(135deg,#2dd4a0,#25b88a)` | `#fff` | 8px | .8rem 1.5rem | sombra mint; hover `translateY(-1px)` |
| `.btn-success` | mismo gradiente | `#fff` | 8px | 7px 16px | 12px/600 |
| `#btn-vender` | gradiente mint | `#fff` | 8px | 10px 16px | 13px/**700**, UPPERCASE, ls .5px, hover `-2px` |
| `.btn-dark` | `rgba(255,255,255,.06)` | `#e8edf2` | 8px | 8–10px 12–16px | borde 1px `#2e353e`; hover mint |
| `.btn-danger` | `rgba(239,90,111,.15)` | `#ef5a6f` | 8px | — | borde `rgba(239,90,111,.3)`; hover .25 |
| `.btn-info` | `rgba(91,156,245,.15)` | `#5b9cf5` | 8px | — | borde `rgba(91,156,245,.3)` |
| `.btn-secondary` | `#2e353e` | `#8892a0` | 8px | 7px 18px | 13px |
| `.btn-outline-*` | transparente | color semántico | 8px | — | hover rellena al 10% |
| `.btn-sm` | — | — | — | .20rem .34rem | font 0.64rem |
| `.scan-btn.add` | gradiente mint | #fff | 8px | 8px 14px | icono 1.2rem |
| `.scan-btn.remove` | `rgba(239,90,111,.15)` | `#ef5a6f` | 8px | 8px 14px | — |

Reglas comunes: `display:inline-flex; align-items:center; justify-content:center; line-height:1;`
`transition:all .2s ease;` activo `transform:scale(.98)`.
`btn-group`: esquinas exteriores 8px, sin radio interno, separador `border-left 1px rgba(0,0,0,.2)`.

### 4.2 Inputs / selects (`form-control`)
```css
min-height:41px; padding:6px 14px; font-size:13px;
color:#e8edf2; background:#1a1e24; border:1px solid #2e353e; border-radius:8px;
/* focus */ border-color:#2dd4a0; box-shadow:0 0 0 2px rgba(45,212,160,.12);
```
- **Select:** `appearance:none` + flecha SVG embebida (`stroke #8892a0`) a `right .75rem`.
- **`input-group-text`:** `min-width:52px; background:#1a1e24; color:#8892a0; border-color:#2e353e`.
- **`input-td`** (cantidad dentro de tabla): centrado, `max-width:50px`, fondo `#1a1e24`.

### 4.3 Tablas (`.table`)
- Header: `12px/600 UPPERCASE`, `color:#8892a0`, `border-bottom:1px #2e353e`.
- Celdas: `padding:8px 6px` (POS) o `12px 14px` (global), `color:#e8edf2`, `vertical-align:middle`.
- Zebra: impar `rgba(255,255,255,.02)`.
- Hover: `rgba(45,212,160,.03–.04)`.
- `.table-scroll`: contenedor con `thead` sticky y borde superior `1px #dee2e6` (en dark se percibe gris).

### 4.4 Paneles y cards
| Clase | Estilo |
|---|---|
| `.x_panel` | `position:relative; padding:10px 17px; border-radius:7px; border:1px solid #2e353e; box-shadow:0 0 9px rgba(0,0,0,.01)` |
| `.dash-panel` | `border:1px solid #2e353e; border-radius:14px; overflow:hidden` + `.panel-header` (padding 18px 22px 14px, borde inferior) + `.panel-body` (padding 6px 0) |
| `.section-card` | `border:1px solid #2e353e; border-radius:10px; padding:15px; background:#1a1e24`; variantes `.cash/.digital/.fondo` con `border-left:4px` |
| `.caja-cerrada-container` | card centrada: `max-width:400px; padding:2rem; border-radius:12px; background:#181b22` |

### 4.5 Tabs (`.nav-tabs`)
- Link: `color:#8892a0; 13px/600; padding:10px 16px; border:none; border-radius:0`.
- Hover: `color:#e8edf2; background:rgba(255,255,255,.03)`.
- Activo: `color:#2dd4a0; border-bottom:2px solid #2dd4a0; background:transparent`.
- Contenedor con `border-bottom:1px solid #2e353e`.

### 4.6 Badges
```css
.badge{ background:rgba(255,255,255,.06); color:#8892a0; font-weight:600; padding:4px 10px; border-radius:6px; }
.badge.bg-info   { background:rgba(91,156,245,.15); color:#5b9cf5; }
.badge.bg-success{ background:rgba(45,212,160,.15); color:#2dd4a0; }
.badge.bg-danger { background:rgba(239,90,111,.15); color:#ef5a6f; }
.badge.bg-warning{ background:rgba(245,180,91,.15); color:#f5b45b; }
```
Badge de contador de menú `.menu-badge`: `min-width:18px; height:18px; radius 9px; fondo #e63757; 0.65rem/700`, con variante de icono (`#ef4444` vencida / `#f59e0b` por vencer / `#3b82f6` revisión).

### 4.7 Modales
1. **Bootstrap (`.modal`)** — usado en `#modalDespacho` y `#modalImagen`:
   `.modal-content{ background:#181b22; border:1px solid #2e353e; border-radius:14px }`,
   header/footer con borde `#2e353e`, título 600, `btn-close{ filter:invert(.7) }`.
2. **Custom (`#modal-container`)** — buscador de productos:
   `.modal-container{ fixed; inset; z-index:9999; display:none }` → `.active{ display:flex }`;
   `.details-modal-overlay{ rgba(0,0,0,.7) }`;
   `.details-modal{ background:#181b22; border-radius:.5em; box-shadow:0 10px 30px rgba(0,0,0,.3); width:80%; height:90vh; padding:1.5em; overflow:auto }`;
   `.details-modal-content{ max-height/height:85vh }`; en ≤563.98px ocupa `100%`.
3. **Diálogo de impresión nativo** (`<dialog>`): dark card, botones "cancelar" (danger soft) y
   "Sí, imprimir" (gradiente mint).
4. **Overlay scanner** (`#section-scanner`): fondo `rgba(0,0,0,.55)` + `backdrop-filter:blur(3px)`,
   tarjeta `#181b22` radio 12px, `min-width:300px; max-width:50%`, animación `modalFadeIn`.

### 4.8 Dropdowns
- `.dropdown-menu{ background:#181b22; border:1px solid #2e353e; border-radius:10–12px; box-shadow:0 8px 24px rgba(0,0,0,.3); min-width:200px }`.
- `.dropdown-item{ color:#8892a0; 13px; padding:9px 16px }`; hover `color:#ef5a6f; background:rgba(239,90,111,.06)` (top-nav) o hover mint en POS.
- `.dropdown-header` con avatar 32px radio 8px.

### 4.9 Loader / estados
- **Loader barra** `.loader` (3 puntos animados, `box-shadow` keyframes) dentro de
  `.contenedor-loader{ position:fixed; inset; background:rgba(0,0,0,.42); z-index:99999; display:grid; place-items:center }`.
- **Carrito vacío** `.cart-empty-state`: columna centrada, icono `ion-icon` 56px `opacity:.4`,
  texto 14px muted, `<b>` en mint. Padding `60px 20px`.
- **Alerta offline** `.alert-danger` (`rgba(239,90,111,.1)` / borde `.2` / texto `#ef5a6f`)
  con icono de advertencia, aparece a lo ancho de la grilla de acciones (`grid-column:1/-1`).

### 4.10 Notificaciones y banners (top-nav)
- `.notif-toggle`: 38×38, radio 8, hover `rgba(255,255,255,.08)`.
- `.notif-item`: fila con icono 32×32 radio 8 y cuerpo; no leído fondo `rgba(45,212,160,.05)` + dot mint 8px.
- **Banner de membresía** (pill): fondo degradado ámbar/info según estado, `border-radius:50px`,
  texto 0.8rem; `≤768px` se oculta. *(En el POS conviene reemplazarlo por Snackbar/Banner nativo.)*

### 4.11 Miniaturas y grilla "más vendidos"
- `.pos-thumb`: 48×48, `object-fit:contain` (o `cover` en `--cover`), radio 8px, fondo `rgba(255,255,255,.04)`, borde `#2e353e`. `td.thumb-cell` ancho 48px.
- `#posTopVendidos`: `display:grid; grid-template-columns:repeat(auto-fill, minmax(180px,1fr)); gap:10px`.
- `.pos-top-item`: flex, padding `8px 10px`, borde `#2e353e`, radio 10px, fondo `rgba(255,255,255,.03)`; hover borde mint.
- `.pos-top-num`: 26×26, radio 6, fondo mint, texto blanco 13/700.
- `.pos-top-precio`: 13/600 en verde; `.pos-top-nombre`: 14px, ellipsis.

---

## 5. Patrones específicos del POS

### 5.1 Tabla del carrito
Columnas: **Cant. · Producto · Peso · BS · Dolares · Acciones**.
Cantidades editables con `.input-td`. Precios con `.text-success/.text-info/.text-danger`.
Totales en `.text-total{ font-size:1rem; font-weight:bold }`.

### 5.2 Grilla de acciones (`.botones-container`)
`display:grid; gap:10px; grid-template-columns:repeat(3,1fr)` →
`repeat(2,1fr)` a **≤992px** → `1fr` a **≤576px**.
Botones: `CRÉDITO`, `DESCONTAR PRODUCTOS`, `RESERVAR CARRITO`, `(C) CALCULAR CAMBIO`,
`(V) VENDER` (primario), `DESTRUIR CARRITO` (danger). Todos UPPERCASE, 12px/600, radio 8.

### 5.3 Aviso de pedidos web
`.pedidos-web-alert`: pill ámbar colapsada a `max-width:46px` que se **expande a 480px en hover**
con texto "Existe N compra(s) web pendiente(s)…"; en táctil (`@media (hover:none)`) siempre expandido.
Icono campana con animación `campanita` 1.4s.

### 5.4 Atajos de teclado
`(B)` abrir búsqueda · `(C)` calcular cambio · `(V)` vender · `Esc` cerrar scanner.

### 5.5 Scanner / alta rápida de producto
Overlay centrado con `.scan-product`: header (nombre + stock), fila `.scan-prices` con
USD (mint), COP/Pesos (info), Bs (danger); `.scan-quantity` (input 70px) y `.scan-btn.add` /
`.scan-btn.remove`.

### 5.6 Modal de despacho (checkout)
- `#resumenVenta`: 3 métricas en `.col-4` con fondos `rgba(...,.12)`:
  PESOS (info), BOLÍVARES (danger), DÓLARES (mint); label 10px, valor 15px/700.
- Badge de tipo: `bg-success` (VENTA) / `bg-warning` (CRÉDITO).
- Select de método de pago con 7 opciones (Punto, BioPago, Pesos, Efectivo, Pago Móvil, Transferencia, Dólares).
- Sección cliente (cédula, nombre, teléfono) mostrable/ocultable según `registro_clientes`.

---

## 6. Estados e interacción
| Estado | Regla |
|---|---|
| Hover botón primario | `translateY(-1px/-2px)` + sombra mint más amplia |
| Active botón | `scale(.98)` |
| Focus input | borde mint + ring `0 0 0 2px rgba(45,212,160,.12)` |
| Focus visible (botón miniatura) | `outline:2px solid #2dd4a0; offset:2px` |
| Transición estándar | `all .2s ease`; menú `.35s cubic-bezier(.16,1,.3,1)` |
| `prefers-reduced-motion` | se anulan transiciones de marca |

---

## 7. Iconografía (web)
- **Boxicons** (`bx bx-*`): caja, productos, membresía (`bx-id-card`), etc.
- **Ionicons** (`ion-icon`): carrito, notificaciones, warnings, menú.
- **Bootstrap Icons** (`bi bi-x-lg`), **Font Awesome**, **Material Icons Round**, **Simple Line Icons**.
- **Android:** unificar en **Material Symbols (Rounded)**. Mapa sugerido:
  carrito→`shopping_cart`, búsqueda→`search`, vender→`point_of_sale`, cerrar caja→`lock`,
  calcular cambio→`calculate`, crédito→`credit_card`, descontar→`percent`, reservar→`bookmark_add`,
  destruir→`delete`, membresía→`badge`, notificaciones→`notifications`, escanear→`qr_code_scanner`.

---

## 8. Ticket / impresión (44 mm)
```css
body{ font-family:Arial; font-size:11px; max-width:44mm; margin:0; }
table{ width:100%; border-collapse:collapse; table-layout:fixed; }
th,td{ text-align:left; padding:2px 0; }
.line{ border-top:1px dashed #000; margin:5px 0; }
@media print{ @page{ size:44mm auto; margin:0; } #ticket{ page-break-after:avoid; } }
```
Estructura: logo (≤50px) → sucursal → fecha → "* Nota de entrega" → items → línea → "¡GRACIAS POR SU COMPRA!" → línea.
**Android:** replicar como plantilla de impresión térmica (ESC/POS o PDF 44mm).

---

## 9. Mapeo a Android (Jetpack Compose + Material 3)

### 9.1 Tema de color
```kotlin
val IsellerDark = darkColorScheme(
    primary            = Color(0xFF2DD4A0), // mint
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFF25B88A), // gradiente mint
    onPrimaryContainer = Color(0xFF06231A),
    secondary          = Color(0xFF5B9CF5), // info
    onSecondary        = Color(0xFFFFFFFF),
    tertiary           = Color(0xFFF5B45B), // warning
    background         = Color(0xFF181B22), // superficie principal
    onBackground       = Color(0xFFE8EDF2),
    surface            = Color(0xFF1A1E24), // elevada / inputs
    onSurface          = Color(0xFFE8EDF2),
    surfaceVariant     = Color(0xFF2E353E), // bordes
    onSurfaceVariant   = Color(0xFF8892A0), // texto secundario
    outline            = Color(0xFF2E353E),
    error              = Color(0xFFEF5A6F),
    onError            = Color(0xFFFFFFFF),
)
```

### 9.2 Formas
```kotlin
val IsellerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),   // botones, inputs, badges
    medium     = RoundedCornerShape(10.dp),  // cards, items
    large      = RoundedCornerShape(14.dp),  // paneles, modales
    extraLarge = RoundedCornerShape(20.dp),
)
```

### 9.3 Tipografía (Roboto)
```kotlin
val IsellerType = Typography(
    titleMedium      = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), // x_title
    bodyMedium       = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),              // body POS
    labelLarge       = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),// botones
    labelMedium      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                 letterSpacing = 0.3.sp),                            // th UPPERCASE
    labelSmall       = TextStyle(fontSize = 11.sp),
)
```

### 9.4 Equivalentes de componente
| Web | Android (M3) |
|---|---|
| `.x_panel` / `.dash-panel` | `Card` / `Surface` + `border(1.dp, outline)` |
| `.btn-primary` / `#btn-vender` | `Button` (primary) con `Brush.linearGradient(mint…)` en tonalidad de marca |
| `.btn-dark` (neutro) | `OutlinedButton` (borde `outline`, texto `onSurface`) |
| `.btn-danger` soft | `FilledTonalButton` (container `error.copy(alpha=.15f)`) |
| `.form-control` | `OutlinedTextField` (foco primary, ring 2dp 12%) |
| `.table` | `LazyColumn` con header sticky; filas zebra `onSurface.copy(.02f)`, hover/press `.04f` |
| `.nav-tabs` | `ScrollableTabRow` (indicador 2dp primary) |
| `.modal-container` / `.modal` | `Dialog` / `ModalBottomSheet` (buscador: full-screen `Dialog`) |
| `.dropdown-menu` | `DropdownMenu` |
| `.badge` | `Badge` / `AssistChip` con los colores .15/soft |
| `.cart-empty-state` | `EmptyState` (icono 56dp, texto muted) |
| `.contenedor-loader` | `CircularProgressIndicator` en overlay scrim |
| Banner membresía | `Snackbar` / `Banner` |
| `.pedidos-web-alert` | `FloatingActionButton` extendida con badge |

### 9.5 Layout
- **Móvil:** el sidebar pasa a **Navigation Drawer modal** (`ModalNavigationDrawer`) — el web ya
  lo hace a ≤992px con overlay.
- **Tablet/landscape:** `PermanentNavigationDrawer` o rail.
- **Nav inferior** opcional para las 4–5 acciones clave (Vender, Búsqueda, Caja, Productos, Membresía).

---

## 10. Accesibilidad y adaptación táctil
- **Tap targets ≥ 48dp**: los botones web tienen `padding 7–10px` + 12–13px de texto (~34–40px),
  subir a 48dp en Android.
- `input-td` de 50px de ancho es pequeño para dedo: usar steppers +/- o input ampliado.
- Mantener contraste: `#e8edf2` sobre `#181b22` ≈ 13:1; `#8892a0` sobre `#181b22` ≈ 5.5:1 (ok AA).
  Mint sobre fondo oscuro es apto para iconos/acentos, **no** para texto de párrafo.
- Conservar estados de foco (ring mint) para navegación con teclado/lectores.
- Respetar `prefers-reduced-motion` → en Android, respetar la escala de animación del sistema.

---

## 11. Referencia rápida

```
FONDO PRINCIPAL     #181B22
FONDO ELEVADO       #1A1E24
BORDE               #2E353E
PRIMARIO / MINT     #2DD4A0   (hover #25B88A)   [app Android: #049FA5, oscuro #038086]
INFO                #5B9CF5
WARNING             #F5B45B
DANGER              #EF5A6F
TEXTO               #E8EDF2
TEXTO MUTED         #8892A0
TINT MINT 12%       rgba(45,212,160,.12)

FUENTE              Roboto (web: Helvetica Neue/Roboto/Arial)
BODY                13px
TÍTULO PANEL        15px / 600
RADIO BASE          8px   (cards 10–14px, badges 6px)
ESPACIADO BASE      4px
GRADIENTE MARCA     linear-gradient(135deg,#2DD4A0,#25B88A)
SOMBRA BOTÓN        0 3px 12px rgba(45,212,160,.25)
SOMBRA FLOTANTE     0 8px 24px rgba(0,0,0,.30)
```

---

## 12. Observaciones para el port (deuda a limpiar)
1. **Doble Bootstrap (4 + 5)** y múltiples librerías de iconos: en Android no aplica, unificar en M3 + Material Symbols.
2. **Fondos en conflicto**: `ventas.php` define `--dash-bg:#1a1e24` pero `custom.min.dark-mode.css` fuerza `right_col:#181b22`. Tomar `#181b22` como superficie base.
3. **`--dash-bg: transparent`** en `global-styles.css` vs `#1a1e24` en el POS: es una redefinición por página; en Android es un único token.
4. **Radios desiguales** para paneles (`x_panel` 7px vs `dash-panel` 14px): normalizar a 10–14px.
5. **Tema Falcon (`--falcon-*`)** convive pero no se usa en el POS: ignorarlo en Android.
6. **z-index hasta 99999** y capas solapadas: en Compose se resuelve con el orden natural de composición.
