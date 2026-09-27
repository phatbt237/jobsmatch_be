/**
 * Shared kernel used by every module. It is OPEN so other modules can use all of its packages,
 * but it must never depend on another module.
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)
package vn.career.common;

import org.springframework.modulith.ApplicationModule;
