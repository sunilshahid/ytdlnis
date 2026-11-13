package com.deniscerri.ytdl.ui.more.cookies

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.auth.YouTubeSessionEngine
import com.deniscerri.ytdl.core.SessionOrchestrator
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.repository.CookieRepository
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.ui.adapter.CookieAdapter
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.isURL
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CookiesFragment : Fragment(), CookieAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var listAdapter: CookieAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var useCookies: MaterialSwitch
    private lateinit var youtubeAccountCookiesLayout: LinearLayout
    private lateinit var youtubeAccountCookiesSwitch: MaterialSwitch
    private lateinit var youtubeAccountSummary: TextView
    private lateinit var checkYoutubeCookiesLayout: LinearLayout
    private lateinit var checkYoutubeCookiesSwitch: MaterialSwitch
    private lateinit var checkYoutubeCookiesStatus: TextView
    private lateinit var checkYoutubeCookiesNow: MaterialButton
    private lateinit var checkCookiesDivider: View
    private lateinit var cookiesViewModel: CookieViewModel
    private lateinit var cookiesList: List<CookieItem>
    private lateinit var noResults: RelativeLayout
    private lateinit var mainActivity: MainActivity
    private lateinit var preferences: SharedPreferences
    private lateinit var youtubeLoginLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Registered in onCreate so view recreation (e.g. returning from the login screen)
        // does not register a second launcher.
        youtubeLoginLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (isAdded) {
                if (result.resultCode == Activity.RESULT_OK && view != null) {
                    Snackbar.make(
                        recyclerView,
                        getString(R.string.youtube_sign_in_success),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                if (::useCookies.isInitialized) {
                    updateYoutubeAccountCookiesState(useCookies.isChecked)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        return inflater.inflate(R.layout.fragment_cookies, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        topAppBar = view.findViewById(R.id.logs_toolbar)
        useCookies = view.findViewById(R.id.use_cookies)
        youtubeAccountCookiesLayout = view.findViewById(R.id.youtube_account_cookies_layout)
        youtubeAccountCookiesSwitch = view.findViewById(R.id.youtube_account_cookies_switch)
        youtubeAccountSummary = view.findViewById(R.id.youtube_account_cookies_summary)
        checkYoutubeCookiesLayout = view.findViewById(R.id.check_youtube_cookies_layout)
        checkYoutubeCookiesSwitch = view.findViewById(R.id.check_youtube_cookies_switch)
        checkYoutubeCookiesStatus = view.findViewById(R.id.check_youtube_cookies_status)
        checkYoutubeCookiesNow = view.findViewById(R.id.check_youtube_cookies_now)
        checkCookiesDivider = view.findViewById(R.id.check_cookies_divider)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        cookiesList = listOf()
        noResults = view.findViewById(R.id.no_results)

        listAdapter =
            CookieAdapter(
                this,
                mainActivity
            )
        val newCookie = view.findViewById<Chip>(R.id.newCookie)
        recyclerView = view.findViewById(R.id.template_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = listAdapter
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getStringSet("swipe_gesture", requireContext().resources.getStringArray(R.array.swipe_gestures_values).toSet())!!.toList().contains("cookies")){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        cookiesViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        cookiesViewModel.items.observe(viewLifecycleOwner) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            cookiesList = it
            listAdapter.submitList(it)
        }
        initMenu()
        initChips()

        val useCookiesPref = preferences.getBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, false)
        val ytAccountCookiesPref = preferences.getBoolean(
            YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES,
            false
        )

        useCookies.isChecked = useCookiesPref
        useCookies.jumpDrawablesToCurrentState()
        newCookie.isEnabled = useCookiesPref

        youtubeAccountCookiesSwitch.isChecked = ytAccountCookiesPref
        youtubeAccountCookiesSwitch.jumpDrawablesToCurrentState()

        val checkCookiesPref = preferences.getBoolean(
            YouTubeSessionEngine.PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN,
            true
        )
        checkYoutubeCookiesSwitch.isChecked = checkCookiesPref
        checkYoutubeCookiesSwitch.jumpDrawablesToCurrentState()

        updateYoutubeAccountCookiesState(useCookiesPref)

        youtubeAccountCookiesLayout.setOnClickListener {
            if (useCookies.isChecked) {
                youtubeAccountCookiesSwitch.toggle()
            }
        }

        checkYoutubeCookiesLayout.setOnClickListener {
            if (checkYoutubeCookiesSwitch.isEnabled) {
                checkYoutubeCookiesSwitch.toggle()
            }
        }

        checkYoutubeCookiesNow.setOnClickListener {
            checkYoutubeSessionNow()
        }

        useCookies.setOnCheckedChangeListener { _, isChecked ->
            newCookie.isEnabled = isChecked
            preferences.edit().putBoolean(YouTubeSessionEngine.PREF_USE_COOKIES, isChecked).apply()
            updateYoutubeAccountCookiesState(isChecked)
        }

        youtubeAccountCookiesSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit()
                .putBoolean(YouTubeSessionEngine.PREF_YOUTUBE_ACCOUNT_COOKIES, isChecked)
                .apply()
            updateYoutubeAccountCookiesState(useCookies.isChecked)
        }

        checkYoutubeCookiesSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit()
                .putBoolean(YouTubeSessionEngine.PREF_CHECK_YOUTUBE_COOKIES_ON_OPEN, isChecked)
                .apply()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::useCookies.isInitialized) {
            updateYoutubeAccountCookiesState(useCookies.isChecked)
        }
    }

    private fun updateYoutubeAccountCookiesState(masterEnabled: Boolean) {
        val ytAccountEnabled = masterEnabled && youtubeAccountCookiesSwitch.isChecked
        youtubeAccountCookiesSwitch.isEnabled = masterEnabled
        youtubeAccountCookiesLayout.isEnabled = masterEnabled
        youtubeAccountCookiesLayout.alpha = if (masterEnabled) 1.0f else 0.45f

        checkCookiesDivider.visibility = if (ytAccountEnabled) View.VISIBLE else View.GONE
        checkYoutubeCookiesLayout.visibility = if (ytAccountEnabled) View.VISIBLE else View.GONE
        checkYoutubeCookiesSwitch.isEnabled = ytAccountEnabled
        checkYoutubeCookiesLayout.isEnabled = ytAccountEnabled
        checkYoutubeCookiesNow.isVisible = ytAccountEnabled
        checkYoutubeCookiesNow.isEnabled = ytAccountEnabled

        if (ytAccountEnabled) {
            val status = preferences.getString(
                YouTubeSessionEngine.PREF_LAST_COOKIE_CHECK_STATUS,
                null
            )
            val lastCheckTime = preferences.getLong(
                YouTubeSessionEngine.PREF_LAST_COOKIE_CHECK_TIMESTAMP,
                0L
            )
            if (status != null && lastCheckTime > 0) {
                checkYoutubeCookiesStatus.visibility = View.VISIBLE
                val statusText = when (status) {
                    YouTubeSessionEngine.COOKIE_STATUS_HEALTHY ->
                        getString(R.string.youtube_cookie_status_healthy)

                    YouTubeSessionEngine.COOKIE_STATUS_EXPIRED ->
                        getString(R.string.youtube_cookie_status_expired)

                    YouTubeSessionEngine.COOKIE_STATUS_SIGNED_OUT ->
                        getString(R.string.youtube_cookie_status_signed_out)

                    YouTubeSessionEngine.COOKIE_STATUS_ERROR ->
                        getString(R.string.youtube_cookie_status_error)

                    else -> getString(R.string.youtube_cookie_status_unknown)
                }
                val relative = DateUtils.getRelativeTimeSpanString(
                    lastCheckTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                checkYoutubeCookiesStatus.text = getString(
                    R.string.youtube_cookie_status_last_checked,
                    relative,
                    statusText
                )
            } else {
                checkYoutubeCookiesStatus.visibility = View.GONE
            }
        }

        val sessionActive = YouTubeSessionEngine.hasValidSession()
        val email = YouTubeSessionEngine.getCachedEmail(requireContext())
        youtubeAccountSummary.text = when {
            !masterEnabled -> getString(R.string.youtube_account_cookies_desc)
            sessionActive && !email.isNullOrBlank() -> getString(R.string.signed_in_as, email)
            sessionActive -> getString(
                R.string.signed_in_as,
                getString(R.string.select_youtube_account)
            )

            else -> getString(R.string.youtube_not_signed_in)
        }

        val isAccountActionVisible = masterEnabled && youtubeAccountCookiesSwitch.isChecked
        topAppBar.menu.findItem(R.id.account_selection)?.isVisible = isAccountActionVisible
    }

    private fun initChips() {
        val new = view?.findViewById<Chip>(R.id.newCookie)
        new?.setOnClickListener {
            showBottomSheet(null)
        }
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.account_selection -> {
                    showYoutubeAccountDialog()
                }
                R.id.delete_cookies -> {
                    val deleteDialog = MaterialAlertDialogBuilder(requireContext())
                    deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                    deleteDialog.setMessage(getString(R.string.confirm_delete_cookies_desc))
                    deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                    deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                        cookiesViewModel.deleteAll()
                        runCatching {
                            FileUtil.getCookieFile(requireContext(), true){
                                File(it).apply { writeText("") }
                            }
                        }
                    }
                    deleteDialog.show()
                }
                R.id.import_clipboard -> {
                    lifecycleScope.launch(Dispatchers.IO){
                        cookiesViewModel.importFromClipboard()
                    }
                }
                R.id.export_clipboard -> {
                    cookiesViewModel.exportToClipboard()
                    Snackbar.make(recyclerView, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                }
                R.id.export_file -> {
                    cookiesViewModel.exportToFile {f ->
                        if (f == null){
                            Snackbar.make(recyclerView, getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG).show()
                        }else{
                            val snack = Snackbar.make(recyclerView, getString(R.string.backup_created_successfully), Snackbar.LENGTH_LONG)
                            snack.setAction(R.string.share) {
                                FileUtil.shareFileIntent(requireContext(), listOf(f.absolutePath))
                            }
                            snack.show()
                        }
                    }
                }
            }
            true
        }
    }

    private fun showYoutubeAccountDialog() {
        if (!YouTubeSessionEngine.hasValidSession()) {
            startYoutubeLogin()
            return
        }

        val email = YouTubeSessionEngine.getCachedEmail(requireContext())
        val accountLabel = email ?: getString(R.string.select_youtube_account)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manage_youtube_account)
            .setMessage(getString(R.string.signed_in_as, accountLabel))
            .setPositiveButton(R.string.refresh_session) { _, _ ->
                refreshYoutubeSession()
            }
            .setNegativeButton(R.string.remove_youtube_account) { _, _ ->
                confirmRemoveYoutubeAccount()
            }
            .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startYoutubeLogin() {
        youtubeLoginLauncher.launch(Intent(requireContext(), YouTubeLoginActivity::class.java))
    }

    private fun refreshYoutubeSession() {
        val progressSnackbar = Snackbar.make(
            recyclerView,
            getString(R.string.refreshing_youtube_cookies),
            Snackbar.LENGTH_INDEFINITE
        )
        progressSnackbar.show()

        lifecycleScope.launch {
            val outcome = SessionOrchestrator.refreshYoutubeSession(requireContext())
            withContext(Dispatchers.Main) {
                progressSnackbar.dismiss()
                if (!isAdded || view == null) return@withContext
                updateYoutubeAccountCookiesState(useCookies.isChecked)

                when (outcome) {
                    SessionOrchestrator.RefreshOutcome.SUCCESS -> {
                        Snackbar.make(
                            recyclerView,
                            getString(R.string.account_cookies_refreshed),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }

                    SessionOrchestrator.RefreshOutcome.NEEDS_MANUAL_LOGIN -> {
                        Snackbar.make(
                            recyclerView,
                            getString(R.string.youtube_sign_in_required),
                            Snackbar.LENGTH_LONG
                        ).setAction(getString(R.string.youtube_sign_in_now)) {
                            startYoutubeLogin()
                        }.show()
                    }

                    SessionOrchestrator.RefreshOutcome.FAILED -> {
                        Snackbar.make(
                            recyclerView,
                            getString(R.string.cookie_refresh_failed),
                            Snackbar.LENGTH_LONG
                        ).setAction(getString(R.string.youtube_sign_in_now)) {
                            startYoutubeLogin()
                        }.show()
                    }
                }
            }
        }
    }

    /**
     * User-initiated health probe. Unlike the throttled startup path this always hits the
     * network, then attempts one silent heal only when the session is actually expired.
     */
    private fun checkYoutubeSessionNow() {
        val progressSnackbar = Snackbar.make(
            recyclerView,
            getString(R.string.youtube_cookie_status_checking),
            Snackbar.LENGTH_INDEFINITE
        )
        progressSnackbar.show()

        lifecycleScope.launch {
            val healthy = SessionOrchestrator.checkAndHealYoutubeSessionIfNeeded(
                requireContext(),
                force = true
            )
            withContext(Dispatchers.Main) {
                progressSnackbar.dismiss()
                if (!isAdded || view == null) return@withContext
                updateYoutubeAccountCookiesState(useCookies.isChecked)

                val status = YouTubeSessionEngine.getCookieCheckStatus(requireContext())
                val message = when (status) {
                    YouTubeSessionEngine.COOKIE_STATUS_HEALTHY ->
                        R.string.youtube_cookie_status_healthy

                    YouTubeSessionEngine.COOKIE_STATUS_EXPIRED ->
                        R.string.youtube_cookie_status_expired

                    YouTubeSessionEngine.COOKIE_STATUS_SIGNED_OUT ->
                        R.string.youtube_cookie_status_signed_out

                    YouTubeSessionEngine.COOKIE_STATUS_ERROR ->
                        R.string.youtube_cookie_status_error

                    else -> R.string.youtube_cookie_status_unknown
                }
                val snackbar = Snackbar.make(recyclerView, getString(message), Snackbar.LENGTH_LONG)
                if (!healthy && status == YouTubeSessionEngine.COOKIE_STATUS_EXPIRED) {
                    snackbar.setAction(getString(R.string.youtube_sign_in_now)) {
                        startYoutubeLogin()
                    }
                }
                snackbar.show()
            }
        }
    }

    private fun confirmRemoveYoutubeAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_youtube_account)
            .setMessage(R.string.remove_youtube_account_confirmation)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.ok) { _, _ ->
                YouTubeSessionEngine.removeAccountAndWipeSession(requireContext()) {
                    if (!isAdded || view == null) return@removeAccountAndWipeSession
                    updateYoutubeAccountCookiesState(useCookies.isChecked)
                    Snackbar.make(
                        recyclerView,
                        getString(R.string.youtube_account_removed),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun showBottomSheet(item: CookieItem?, position: Int = 0){
        lifecycleScope.launch {
            val layout = BottomSheetDialog(requireContext())
            layout.requestWindowFeature(Window.FEATURE_NO_TITLE)
            layout.setContentView(R.layout.cookie_bottom_sheet)

            val urlEditText = layout.findViewById<EditText>(R.id.url_edittext)!!
            val getCookies = layout.findViewById<MaterialButton>(R.id.getCookiesBtn)!!

            urlEditText.doAfterTextChanged { _ ->
                getCookies.isEnabled = urlEditText.text.toString().isURL()
            }

            val urlText = item?.url ?: "https://"
            urlEditText.setText(urlText)
            urlEditText.setSelection(urlEditText.text.length)

            val descriptionEditText = layout.findViewById<EditText>(R.id.title_edittext)!!
            descriptionEditText.setText(item?.description ?: "")
            layout.findViewById<TextInputLayout>(R.id.description_input_layout)?.isVisible = item != null

            val incognitoSwitch = layout.findViewById<MaterialSwitch>(R.id.incognito_switch)!!

            val current = layout.findViewById<MaterialCardView>(R.id.current)!!
            current.isVisible = item != null
            item?.apply {
                current.findViewById<TextView>(R.id.currentText).apply {
                    setText(item.content)
                    enableTextHighlight()
                }
            }

            val clipboard = layout.findViewById<MaterialButton>(R.id.clipboard)!!
            val save = layout.findViewById<MaterialButton>(R.id.save)!!
            save.isVisible = item != null

            getCookies.setOnClickListener {
                val intent = Intent(
                    requireContext(),
                    if (incognitoSwitch.isChecked) WebViewActivityIncognito::class.java else WebViewActivity::class.java
                )
                intent.putExtra("url", urlEditText.text.toString())
                intent.putExtra("description", descriptionEditText.text.toString())
                layout.dismiss()
                startActivity(intent)
            }

            item?.apply {
                clipboard.setOnClickListener {
                    UiUtil.copyToClipboard(CookieRepository.cookieHeader + "\n" + item.content, requireActivity())
                    layout.dismiss()
                }

                getCookies.text = getString(R.string.update)

                save.setOnClickListener {
                    item.description = descriptionEditText.text.toString()
                    item.url = urlEditText.text.toString()
                    cookiesViewModel.update(item)
                    listAdapter.notifyItemChanged(position)
                    layout.dismiss()
                }
            }

            val imm = mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            urlEditText.postDelayed({
                urlEditText.requestFocus()
                imm.showSoftInput(urlEditText, 0)
            }, 300)

            layout.show()
            layout.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            layout.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        }

    }

    override fun onItemClick(cookieItem: CookieItem, position: Int) {
        showBottomSheet(cookieItem, position)
    }

    override fun onSelected(cookieItem: CookieItem) {
    }

    override fun onItemEnabledChanged(cookieItem: CookieItem, isEnabled: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                cookiesViewModel.changeCookieEnabledState(cookieItem.id, isEnabled)
            }
        }
    }

    override fun onDelete(cookieItem: CookieItem) {
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + cookieItem.url + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            cookiesViewModel.delete(cookieItem)
        }
        deleteDialog.show()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = cookiesList[position]
                        listAdapter.notifyItemChanged(position)
                        onDelete(deletedItem)
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    context,
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
}
