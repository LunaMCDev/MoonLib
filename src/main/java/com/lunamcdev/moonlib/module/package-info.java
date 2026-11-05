/**
 * Structure for large plugins: {@link com.lunamcdev.moonlib.module.MoonModule}s with their own
 * lifecycle and automatic cleanup, a {@link com.lunamcdev.moonlib.module.ModuleManager} that orders
 * them by dependency, isolates failures and lets server owners toggle them, and a
 * {@link com.lunamcdev.moonlib.module.Services} registry so modules share interfaces rather than
 * internals. See {@code docs/modules.md}.
 *
 * @since 1.2.0
 */
package com.lunamcdev.moonlib.module;
