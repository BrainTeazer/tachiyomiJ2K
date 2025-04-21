package eu.kanade.tachiyomi.ui.more.stats

import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView

@Composable
fun ListChartLegend(item: StatsController.StatusDistributionItem) {
    Row {
        Icon(imageVector = Icons.Filled.Circle, contentDescription = "", tint = Color(item.color))
        Text(
            item.status,
            modifier = Modifier.padding(4.dp, 0.dp, 0.dp),
        )
        Text(
            item.amount.toString(),
            modifier = Modifier.padding(12.dp, 0.dp, 0.dp),
        )
    }
}

@Composable
@Preview
fun ListChartLegendPreview() {
    ListChartLegend(StatsController.StatusDistributionItem("Publishing Finished", 12, -1))
}

class StatsLegendAdapter(
    private val list: List<StatsController.StatusDistributionItem>,
) : RecyclerView.Adapter<StatsLegendAdapter.StatsLegendHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatsLegendHolder {
        return StatsLegendHolder(ComposeView(parent.context))
    }

    override fun onBindViewHolder(holder: StatsLegendHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class StatsLegendHolder(private val composeView: ComposeView) :
        RecyclerView.ViewHolder(composeView) {
        fun bind(item: StatsController.StatusDistributionItem) {
            composeView.setContent {
                ListChartLegend(item)
            }
        }
    }
}
