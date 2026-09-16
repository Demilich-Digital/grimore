package digital.demilich.grimoire.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A `ComposeView` normally gets its [LifecycleOwner]/[ViewModelStoreOwner]/
 * [SavedStateRegistryOwner] for free from the hosting Activity/Fragment - a raw
 * view attached directly to [android.view.WindowManager] from a Service has none
 * of those, and Compose refuses to compose without them. This is the standard
 * "Compose in an overlay" shim: create one, attach it to the view tree before
 * `windowManager.addView(...)`, and drive it through CREATED -> STARTED -> RESUMED
 * as the overlay window is shown, then back down and DESTROYED when it's removed.
 *
 * One instance per overlay window (the bubble and the panel each need their own),
 * since each is added/removed from WindowManager independently.
 */
class ComposeOverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** Call once, right before adding [view] to the WindowManager. */
    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun onWindowAdded() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Call when the window is removed from the WindowManager for good. */
    fun onWindowRemoved() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
