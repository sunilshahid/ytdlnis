package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.deniscerri.ytdl.util.ApkInstallUtil
import com.deniscerri.ytdl.util.Extensions.applyTactilePressAnimation
import com.google.android.material.color.MaterialColors


abstract class BaseSettingsFragment : PreferenceFragmentCompat(), SettingHost {
    abstract val title: Int

    private lateinit var installLauncher: ActivityResultLauncher<Intent>

    override fun findPref(key: String) = findPreference<Preference>(key)
    @SuppressLint("NotifyDataSetChanged")
    override fun refreshUI() {
        listView.adapter?.notifyDataSetChanged()
    }
    override fun getHostContext() = requireActivity()
    override fun getAppInstallLauncher() = installLauncher
    override val activityResultDelegate = PreferenceActivityResultDelegate(this)
    override fun requestGetParentFragmentManager() = parentFragmentManager
    override fun requestRecreateActivity() = requireActivity().recreate()
    override fun requestNavigate(id: Int) = findNavController().navigate(id)
    override val hostViewModelStoreOwner by lazy {
        this
    }
    override val hostLifecycleOwner by lazy {
        this
    }
    override val hostView by lazy {
        view
    }
    override fun onStart() {
        super.onStart()
        (activity as? SettingsActivity)?.changeTopAppbarTitle(getString(title))
    }

    fun getPreferences(p: Preference, list: MutableList<Preference>) : List<Preference> {
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferences(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    fun resetPreferences(editor: SharedPreferences.Editor, key: Int) {
        getPreferences(preferenceScreen, mutableListOf()).forEach {
            editor.remove(it.key)
        }
        editor.apply()
        PreferenceManager.setDefaultValues(requireActivity().applicationContext, key, true)
    }

    //Thanks libretube
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val shownCustomDialog = DefaultPreferenceActions.onPreferenceDisplayDialog(requireActivity(), preference) {}
        if (!shownCustomDialog) {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installLauncher = ApkInstallUtil.registerInstallLauncher(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)

        val density = resources.displayMetrics.density
        val topPadding = (4 * density).toInt()
        val bottomPadding = (24 * density).toInt()
        listView.clipToPadding = false
        listView.setPadding(listView.paddingLeft, topPadding, listView.paddingRight, bottomPadding)

        listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (view !is TextView) {
                    view.applyTactilePressAnimation()
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {
                view.animate().cancel()
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                v.paddingLeft,
                topPadding,
                v.paddingRight,
                bottomPadding + systemBars.bottom
            )

            insets
        }

        val keyToHighlight = arguments?.getString("highlight_key")
        if (keyToHighlight != null) {
            val adapter = listView.adapter as? PreferenceGroupAdapter
            val position = adapter?.getPreferenceAdapterPosition(keyToHighlight) ?: -1

            if (position != -1) {
                listView.postDelayed({
                    listView.smoothScrollToPosition(position + 1)

                    listView.postDelayed({
                        val holder = listView.findViewHolderForAdapterPosition(position)
                        holder?.itemView?.let { itemView ->
                            val originalTint = itemView.backgroundTintList
                            val highlight = MaterialColors.getColor(
                                requireContext(),
                                com.google.android.material.R.attr.colorSecondaryContainer,
                                Color.GRAY
                            )
                            itemView.backgroundTintList = ColorStateList.valueOf(highlight)
                            itemView.postDelayed({
                                itemView.backgroundTintList = originalTint
                            }, 1000)
                        }
                    }, 300)
                }, 200)
            }
        }
    }
}