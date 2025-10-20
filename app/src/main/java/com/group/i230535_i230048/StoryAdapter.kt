import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.group.i230535_i230048.R
import com.group.i230535_i230048.StoryBubble
import com.group.i230535_i230048.camera_story
import com.group.i230535_i230048.loadUserAvatar

class StoryAdapter(
    private val items: List<StoryBubble>,
    private val currentUid: String
) : RecyclerView.Adapter<StoryAdapter.StoryVH>() {

    inner class StoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)
        val pfp: ImageView = itemView.findViewById(R.id.pfp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_story_bubble, parent, false)
        return StoryVH(view)
    }

    override fun onBindViewHolder(holder: StoryVH, position: Int) {
        val item = items[position]

        val isSelfBubble = (position == 0) || (item.uid == currentUid)
        holder.username.text = if (isSelfBubble) "Your Story" else item.username

        val targetUid = if (isSelfBubble) currentUid else item.uid
        // Load avatar for target user; if their DP is missing, fallback to your DP.
        holder.pfp.loadUserAvatar(
            uid = targetUid,
            fallbackUid = currentUid,
            placeholderRes = R.drawable.person1
        )

        holder.itemView.setOnClickListener {
            val safeUid = if (item.uid.isNotBlank()) item.uid else currentUid
            holder.itemView.context.startActivity(
                Intent(holder.itemView.context, camera_story::class.java).putExtra("uid", safeUid)
            )
        }
    }

    override fun getItemCount() = items.size
}
