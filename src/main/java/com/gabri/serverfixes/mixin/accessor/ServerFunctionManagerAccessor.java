package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor Mixin para ServerFunctionManager.
 *
 * <p>Expõe o campo privado {@code library} (ServerFunctionLibrary) que é o listener
 * real que implementa {@link net.minecraft.server.packs.resources.PreparableReloadListener}.
 * ServerFunctionManager é apenas o gestor de execução; a carga/reload é feita
 * pela ServerFunctionLibrary interna.</p>
 *
 * <p>Usado pelo SurgicalReloadHandler para reload cirúrgico de functions
 * sem acionar o /reload global.</p>
 */
@Mixin(ServerFunctionManager.class)
public interface ServerFunctionManagerAccessor {

    /**
     * Acessa o ServerFunctionLibrary interno do ServerFunctionManager.
     * Este é o PreparableReloadListener responsável pelo carregamento das functions.
     */
    @Accessor("library")
    ServerFunctionLibrary sf_getFunctionLibrary();
}
