# Comandos para programacion con `SDD` e `AI`
Idea de [SSD con IA](https://www.youtube.com/watch?v=5HaOxAAA5qI)

## Estructura del proyecto

```
panel/
├── AGENTS.md
├── .cursor/
    ├── rules/
    |    └── laravel-rules.mdc                # una línea: @AGENTS.md
    └── skills
        └── SKILL.md
├── docs/
│   └── constitution.md
├── specs/
│   └── NNN-<name-in-kebab-case>/
│       ├── spec.md
│       ├── plan.md
│       └── tasks.md
```

## Prompts SDD

### 1. Setup, constitución y AGENTS.md

**Constitución:**

```text
Vamos a crear la constitución de un proyecto nuevo: una CLI en Laravel para
para hacer scraping de distintas fuentes, gestionarlos y mostrarlos en dashboards.

Proponme un docs/constitution.md con 6 principios innegociables, cortos y
verificables, que cubran: simplicidad del stack, relación entre spec y código,
separación entre lógica e interfaz, política de tests, persistencia de datos
e idioma del código y los mensajes. Máximo 15 líneas. Espera mi aprobación.
```

*Genera el [/docs/constitution.md](./docs/constitution.md)*

*Escribimos el [AGENTS.md](./AGENTS.md) y el [laravel-rules.mdc](./cursor/rules/laravel-rules.mdc)*

**Especificación:**

```text
NO escribas código en ningún momento. Vamos a redactar una nueva funcionalidad. Lee docs/constitution.md.

En el listado de Clipping, si un usuario intenta eliminar una noticia que pertenece a uno o más agrupamientos, el sistema bloquea el borrado y muestra un modal con los nombres de esos agrupamientos, sin ofrecer una forma de continuar. El objetivo es que, desde ese mismo modal, el usuario pueda eliminar el articulo y su referencia en los agrupamientos.

Tu trabajo:
1. Hazme preguntas de UNA en UNA para eliminar ambigüedades (casos límite,
   comportamiento con errores, qué queda fuera del MVP). Máximo 6 preguntas.
2. Con mis respuestas, genera specs/NNN-<name-in-kebab-case>/spec.md con esta estructura:
   contexto y objetivo, usuarios, historias de usuario, requisitos funcionales
   numerados (RF-x) con criterios de aceptación en notación EARS en español,
   requisitos no funcionales, casos límite, fuera de alcance, criterios de
   finalización y dudas abiertas marcadas como [NECESITA ACLARACIÓN].
3. El QUÉ y el POR QUÉ. Nada de stack, arquitectura ni nombres de archivos:
   eso irá en el plan.
```

*Genera el [NNN-name-in-kebab-case/spec.md](./NNN-<name-in-kebab-case>/spec.md)*

**Clarificación:**

```text
Revisa specs/NNN-<name-in-kebab-case>.md como si fueras un QA muy profesional.
Lista: (1) ambigüedades restantes, (2) contradicciones entre requisitos,
(3) casos límite no cubiertos, (4) conflictos con docs/constitution.md.
No propongas soluciones todavía: solo detecta. Formato: lista numerada.
```

**Planificación:**

```text
Lee el @docs/constitution.md y @specs/NNN-<name-in-kebab-case>/spec.md. No escribas codigo.
Genera el plan.md justo al lado de la spec con las funcionalidades explicadas en @specs/NNN-<name-in-kebab-case>/spec.md.
Todo debe respetar la @docs/constitution.md y cubrir todos los RF. Marca que RF cubre cada parte
```

*Genera el [specs/NNN-name-in-kebab-case/plan.md](./specs/NNN-<name-in-kebab-case>/plan.md)*

**Tareas:**

```text
A partir de @specs/NNN-<name-in-kebab-case>/spec.md y @specs/NNN-<name-in-kebab-case>/plan.md genera tasks.md al lado de esos dos archivos. No escribas codigo:
tareas pequeñas (máx. 20-30 min cada una), en orden de dependencia, cada una
con los RF que cubre y una línea "Hecho cuando:" verificable. Usa checkboxes.
```

*Genera el [specs/NNN-name-in-kebab-case/tasks.md](./specs/NNN-<name-in-kebab-case>/tasks.md)*

**Implementación:**

```text
Implementa SOLO la tarea T1 de @specs/NNN-<name-in-kebab-case>/tasks.md, siguiendo @specs/NNN-<name-in-kebab-case>/plan.md y la @docs/constitution.md. Al terminar: marca T1 en tasks.md,
indica qué RF cubre y PÁRATE. No empieces T2.
...
Ahora quiero que implementes T2 y pares ahi, no implementes T3
.....
```

*Genera la implementación del codigo segun tasks.md MANTENIENDO EL CONTEXTO*
>Tambien puedes pedirle en este ultimo paso que implemente completo las tasks (es mas dificil seguir los cambios y por que se implementan)