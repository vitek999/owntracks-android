package org.owntracks.android.ui.base;

import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableList;
import androidx.databinding.OnRebindCallback;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.lang.ref.WeakReference;
import java.util.List;

import timber.log.Timber;

public class BaseAdapter<T> extends RecyclerView.Adapter<ViewHolder> implements View.OnClickListener, View.OnLongClickListener {
    static final Object DATA_INVALIDATION = new Object();

    @NonNull
    private final BaseAdapterItemView itemViewArg;
    private final WeakReferenceOnListChangedCallback<T> callback = new WeakReferenceOnListChangedCallback<>(this);
    private WeakReference<ClickListener> clickListenerWeakReference;
    private List<T> items;
    private LayoutInflater inflater;
    private ItemIds<T> itemIds;

    // Currently attached recyclerview, we don't have to listen to notifications if null.
    @Nullable
    private RecyclerView recyclerView;

    protected BaseAdapter(@NonNull BaseAdapterItemView arg) {
        this.itemViewArg = arg;
    }

    @NonNull
    public BaseAdapterItemView getItemViewArg() {
        return itemViewArg;
    }

    protected void setItems(@Nullable List<T> items) {
        if (this.items == items) {
            return;
        }
        // If a recyclerview is listening, set up listeners. Otherwise wait until one is attached.
        // No need to make a sound if nobody is listening right?
        if (recyclerView != null) {
            if (this.items instanceof ObservableList) {
                ((ObservableList<T>) this.items).removeOnListChangedCallback(callback);
            }
            if (items instanceof ObservableList) {
                ((ObservableList<T>) items).addOnListChangedCallback(callback);
            }
        }
        this.items = items;
        notifyDataSetChanged();
    }

    private T getAdapterItem(int position) {
        return items.get(position);
    }

    private ViewDataBinding onCreateBinding(LayoutInflater inflater, @LayoutRes int layoutId, ViewGroup viewGroup) {
        return DataBindingUtil.inflate(inflater, layoutId, viewGroup, false);
    }

    private void onBindBinding(ViewDataBinding binding, int bindingVariable, @LayoutRes int layoutRes, int position, T item) {
        if (bindingVariable != BaseAdapterItemView.BINDING_VARIABLE_NONE) {
            boolean result = binding.setVariable(bindingVariable, item);
            if (!result) {
                Timber.e("Unable to bind %s to %s", bindingVariable, item);
            }
            binding.executePendingBindings();
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView == null && items instanceof ObservableList) {
            ((ObservableList<T>) items).addOnListChangedCallback(callback);
        }
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView != null && items instanceof ObservableList) {
            ((ObservableList<T>) items).removeOnListChangedCallback(callback);
        }
        this.recyclerView = null;
    }

    @Override
    public final ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int layoutId) {
        if (inflater == null) {
            inflater = LayoutInflater.from(viewGroup.getContext());
        }
        ViewDataBinding binding = onCreateBinding(inflater, layoutId, viewGroup);
        binding.getRoot().setOnClickListener(this);
        binding.getRoot().setOnLongClickListener(this);
        final ViewHolder holder = onCreateViewHolder(binding);
        binding.addOnRebindCallback(new OnRebindCallback() {
            @Override
            public boolean onPreBind(ViewDataBinding binding) {
                return isRecyclerReady();
            }

            @Override
            public void onCanceled(ViewDataBinding binding) {
                if (!isRecyclerReady()) {
                    return;
                }
                int position = holder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(position, DATA_INVALIDATION);
                }
            }
        });
        return holder;
    }

    boolean isRecyclerReady() {
        return recyclerView != null && recyclerView.isComputingLayout();
    }

    private ViewHolder onCreateViewHolder(ViewDataBinding binding) {
        return new BindingViewHolder(binding);
    }

    @Override
    public void onClick(View view) {
        ClickListener<T> listener = clickListenerWeakReference.get();
        if (listener != null)
            if (recyclerView != null) {
                listener.onClick(getAdapterItem(recyclerView.getChildLayoutPosition(view)), view, false);
            }

    }

    @Override
    public boolean onLongClick(View view) {
        ClickListener<T> listener = clickListenerWeakReference.get();
        if (listener != null)
            if (recyclerView != null) {
                listener.onClick(getAdapterItem(recyclerView.getChildLayoutPosition(view)), view, true);
            }
        return false;
    }

    private static class BindingViewHolder extends RecyclerView.ViewHolder {
        BindingViewHolder(ViewDataBinding binding) {
            super(binding.getRoot());
        }
    }

    @Override
    public final void onBindViewHolder(ViewHolder viewHolder, int position) {
        T item = items.get(position);
        ViewDataBinding binding = DataBindingUtil.getBinding(viewHolder.itemView);
        onBindBinding(binding, itemViewArg.bindingVariable(), itemViewArg.layoutRes(), position, item);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (isForDataBinding(payloads)) {
            ViewDataBinding binding = DataBindingUtil.getBinding(holder.itemView);
            binding.executePendingBindings();
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private boolean isForDataBinding(List<Object> payloads) {
        if (payloads == null || payloads.size() == 0) {
            return false;
        }
        for (int i = 0; i < payloads.size(); i++) {
            Object obj = payloads.get(i);
            if (obj != DATA_INVALIDATION) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return itemViewArg.layoutRes();
    }

    /**
     * Set the item id's for the items. If not null, this will set {@link
     * RecyclerView.Adapter#setHasStableIds(boolean)} to true.
     */
    public void setItemIds(@Nullable ItemIds<T> itemIds) {
        this.itemIds = itemIds;
        setHasStableIds(itemIds != null);
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public long getItemId(int position) {
        return itemIds == null ? position : itemIds.getItemId(position, items.get(position));
    }

    private static class WeakReferenceOnListChangedCallback<T> extends ObservableList.OnListChangedCallback<ObservableList<T>> {
        final WeakReference<BaseAdapter<T>> adapterRef;

        WeakReferenceOnListChangedCallback(BaseAdapter<T> adapter) {
            this.adapterRef = new WeakReference<>(adapter);
        }

        @Override
        public void onChanged(ObservableList sender) {
            BaseAdapter<T> adapter = adapterRef.get();
            if (adapter == null) {
                return;
            }
            ensureChangeOnMainThread();
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(ObservableList sender, final int positionStart, final int itemCount) {
            BaseAdapter<T> adapter = adapterRef.get();
            if (adapter == null) {
                return;
            }
            ensureChangeOnMainThread();
            adapter.notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeInserted(ObservableList sender, final int positionStart, final int itemCount) {
            BaseAdapter<T> adapter = adapterRef.get();
            if (adapter == null) {
                return;
            }
            ensureChangeOnMainThread();
            adapter.notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void onItemRangeMoved(ObservableList sender, final int fromPosition, final int toPosition, final int itemCount) {
            BaseAdapter<T> adapter = adapterRef.get();
            if (adapter == null) {
                return;
            }
            ensureChangeOnMainThread();
            for (int i = 0; i < itemCount; i++) {
                adapter.notifyItemMoved(fromPosition + i, toPosition + i);
            }
        }

        @Override
        public void onItemRangeRemoved(ObservableList sender, final int positionStart, final int itemCount) {
            BaseAdapter<T> adapter = adapterRef.get();
            if (adapter == null) {
                return;
            }
            ensureChangeOnMainThread();
            adapter.notifyItemRangeRemoved(positionStart, itemCount);
        }
    }

    interface ItemIds<T> {
        long getItemId(int position, T item);
    }

    static void ensureChangeOnMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new IllegalStateException("You must only modify the ObservableList on the main thread.");
        }
    }

    public interface ClickListener<T> {
        void onClick(@NonNull T object, @NonNull View view, boolean longClick);
    }

    protected void setClickListener(ClickListener listener) {
        this.clickListenerWeakReference = new WeakReference<>(listener);
    }

}

