package apps.chocolatecakecodes.bluebeats.view

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

internal class SearchViewModel : ViewModel() {

    enum class Grouping { FILENAME, TITLE, ID3_TAG, USER_TAG }

    val grouping = MutableLiveData(Grouping.FILENAME)
}
