import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.rayaanraza.i230535.R
import com.rayaanraza.i230535.StoryBubble
import com.rayaanraza.i230535.camera_story

class StoryAdapter(private val items: List<StoryBubble>) :
    RecyclerView.Adapter<StoryAdapter.StoryVH>() {

    inner class StoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)   // from item_story_bubble.xml
        val pfp: ImageView = itemView.findViewById(R.id.pfp)            // from item_story_bubble.xml
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_story_bubble, parent, false)
        return StoryVH(view)
    }

    override fun onBindViewHolder(holder: StoryVH, position: Int) {
        val item = items[position]

        holder.username.text = item.username

        val ctx = holder.itemView.context
        if (!item.profileUrl.isNullOrBlank()) {
            Glide.with(ctx)
                .load(item.profileUrl)
                .placeholder(R.drawable.person1)
                .error(R.drawable.person1)
                .into(holder.pfp)
        } else {
            holder.pfp.setImageResource(R.drawable.person1)
        }

        holder.itemView.setOnClickListener {
            ctx.startActivity(
                Intent(ctx, camera_story::class.java).putExtra("uid", item.uid)
            )
        }
    }

    override fun getItemCount() = items.size
}
