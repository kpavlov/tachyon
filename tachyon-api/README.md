# Tachyon API

Public contracts shared by Tachyon runtimes and adapters.

- Owns annotations, DTOs, handler SPIs, descriptors, registry façades, and transport-neutral configuration.
- Depends only on JDK types and libraries exposed by its public contracts.
- Never depends on `tachyon-core`, Netty, SLF4J, or NetworkNT.
- `tachyon-core` implements and depends on this module.
- `@ExperimentalApi` and `@InternalApi` are outside the stable compatibility promise.
