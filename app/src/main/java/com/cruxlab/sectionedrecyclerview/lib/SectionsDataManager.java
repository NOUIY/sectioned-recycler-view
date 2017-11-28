package com.cruxlab.sectionedrecyclerview.lib;


import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores and manages all data for RecyclerView sections.
 * <p>
 * Items in RecyclerView are divided into groups - sections. Each section consists of regular items
 * and an optional header (just another item for the RecyclerView.Adapter implementation), which can
 * be represented as views using corresponding {@link SimpleSectionAdapter} or {@link SectionAdapter}.
 * <p>
 * Each section obtains own unique type stored in {@link #sectionToType}. It is used to determine
 * that the section which corresponds to the given global adapter position has changed, so the
 * corresponding ViewHolder should be recreated. Each BaseSectionAdapter also can use short values
 * to distinguish own items.
 * <p>
 * The main task is to determine, which section corresponds to the given global adapter position and
 * whether it is a header or a regular item in it. To do it efficiently partial sum array is used
 * {@link #sectionToPosSum}, where on the i-th position is the number of items in RecyclerView in all
 * sections before i-th inclusive, and binary search (e.g. {@link #calcSection(int)}).
 * For details on how RecyclerView interacts with sections see the RecyclerView.Adapter {@link #adapter}
 * and ItemTouchHelper.Callback {@link #swipeCallback} implementations.
 *
 */
public class SectionsDataManager implements SectionManager, PositionConverter {

    private short freeType = 1;
    private ArrayList<Integer> sectionToPosSum;
    private ArrayList<Short> sectionToType;
    private SparseArray<SectionAdapterWrapper> typeToAdapter;
    private SparseArray<SectionItemSwipeCallback> typeToCallback;
    private SparseArray<Integer> headerTypeToCnt;

    private HeaderManager headerManager;

    public SectionsDataManager() {
        sectionToPosSum = new ArrayList<>();
        sectionToType = new ArrayList<>();
        typeToAdapter = new SparseArray<>();
        typeToCallback = new SparseArray<>();
        headerTypeToCnt = new SparseArray<>();
    }

    /***
     * Returns Adapter for RecyclerView, which interacts with sections passing calls to
     * SectionAdapters.
     *
     * @return RecyclerView.Adapter implementation.
     */
    public RecyclerView.Adapter<ViewHolderWrapper> getAdapter() {
        return adapter;
    }

    /**
     * Returns Callback for RecyclerView's ItemTouchHelper, which interacts with sections passing
     * calls to SectionItemSwipeCallbacks.
     *
     * @return ItemTouchHelper.Callback implementation.
     */
    public ItemTouchHelper.Callback getSwipeCallback() {
        return swipeCallback;
    }

    /**
     * Creates {@link HeaderManager} to interact with {@link SectionHeaderLayout}.
     *
     * @param headerViewManager HeaderViewManager to interact with.
     */
    HeaderManager createHeaderManager(HeaderViewManager headerViewManager) {
        return headerManager = new HeaderManager(headerViewManager);
    }

    /* SECTION MANAGER */

    @Override
    public int getSectionCount() {
        return typeToAdapter.size();
    }

    @Override
    public void addSection(@NonNull SimpleSectionAdapter simpleSectionAdapter) {
        addSection(simpleSectionAdapter, null);
    }

    @Override
    public void addSection(@NonNull SectionAdapter sectionAdapter, int headerType) {
        addSection(sectionAdapter, null, headerType);
    }

    @Override
    public void addSection(@NonNull SimpleSectionAdapter simpleSectionAdapter, SectionItemSwipeCallback swipeCallback) {
        addSection(new SectionAdapterWrapper(simpleSectionAdapter), swipeCallback);
    }

    @Override
    public void addSection(@NonNull SectionAdapter sectionAdapter, SectionItemSwipeCallback swipeCallback, int headerType) {
        checkHeaderType(headerType);
        updateHeaderTypeCnt(headerType, 1);
        addSection(new SectionAdapterWrapper(sectionAdapter, headerType), swipeCallback);
    }

    private void addSection(SectionAdapterWrapper adapterWrapper, SectionItemSwipeCallback swipeCallback) {
        checkFreeType();
        adapterWrapper.setSection(getSectionCount());
        adapterWrapper.setItemManager(sectionItemManager);
        int start = getTotalItemCount();
        int cnt = adapterWrapper.getItemCount() + adapterWrapper.getHeaderVisibilityInt();
        int posSum = getTotalItemCount() + cnt;
        typeToAdapter.put(freeType, adapterWrapper);
        if (swipeCallback != null) {
            typeToCallback.put(freeType, swipeCallback);
        }
        sectionToType.add(freeType);
        sectionToPosSum.add(posSum);
        freeType++;
        adapter.notifyItemRangeInserted(start, cnt);
        if (headerManager != null) {
            headerManager.checkFirstVisiblePos();
        }
    }

    @Override
    public void insertSection(int section, @NonNull SimpleSectionAdapter simpleSectionAdapter) {
        insertSection(section, simpleSectionAdapter, null);
    }

    @Override
    public void insertSection(int section, @NonNull SectionAdapter sectionAdapter, int headerType) {
        insertSection(section, sectionAdapter, null, headerType);
    }

    @Override
    public void insertSection(int section, @NonNull SimpleSectionAdapter simpleSectionAdapter, SectionItemSwipeCallback swipeCallback) {
        insertSection(section, new SectionAdapterWrapper(simpleSectionAdapter), swipeCallback);
    }

    @Override
    public void insertSection(int section, @NonNull SectionAdapter sectionAdapter, SectionItemSwipeCallback swipeCallback, int headerType) {
        checkHeaderType(headerType);
        updateHeaderTypeCnt(headerType, 1);
        insertSection(section, new SectionAdapterWrapper(sectionAdapter, headerType), swipeCallback);
    }

    private void insertSection(int section, SectionAdapterWrapper adapterWrapper, SectionItemSwipeCallback swipeCallback) {
        checkFreeType();
        checkSectionIndex(section, true);
        adapterWrapper.setSection(section);
        adapterWrapper.setItemManager(sectionItemManager);
        int start = getSectionFirstPos(section);
        int cnt = adapterWrapper.getItemCount() + adapterWrapper.getHeaderVisibilityInt();
        int posSum = (section > 0 ? sectionToPosSum.get(section - 1) : 0) + cnt;
        typeToAdapter.put(freeType, adapterWrapper);
        if (swipeCallback != null) {
            typeToCallback.put(freeType, swipeCallback);
        }
        sectionToType.add(section, freeType);
        sectionToPosSum.add(section, posSum);
        freeType++;
        updatePosSum(section + 1, cnt, true);
        adapter.notifyItemRangeInserted(start, cnt);
        if (headerManager != null) {
            headerManager.checkFirstVisiblePos();
        }
    }

    @Override
    public void replaceSection(int section, @NonNull SimpleSectionAdapter simpleSectionAdapter) {
        replaceSection(section, simpleSectionAdapter, null);
    }

    @Override
    public void replaceSection(int section, @NonNull SectionAdapter sectionAdapter, int headerType) {
        replaceSection(section, sectionAdapter, null, headerType);
    }

    @Override
    public void replaceSection(int section, @NonNull SimpleSectionAdapter simpleSectionAdapter, SectionItemSwipeCallback swipeCallback) {
        replaceSection(section, new SectionAdapterWrapper(simpleSectionAdapter), swipeCallback);
    }

    @Override
    public void replaceSection(int section, @NonNull SectionAdapter sectionAdapter, SectionItemSwipeCallback swipeCallback, int headerType) {
        checkHeaderType(1);
        updateHeaderTypeCnt(headerType, 1);
        replaceSection(section, new SectionAdapterWrapper(sectionAdapter, headerType), swipeCallback);
    }

    private void replaceSection(int section, SectionAdapterWrapper adapterWrapper, SectionItemSwipeCallback swipeCallback) {
        checkSectionIndex(section);
        removeSection(section);
        if (section == getSectionCount()) {
            addSection(adapterWrapper, swipeCallback);
        } else {
            insertSection(section, adapterWrapper, swipeCallback);
        }
    }

    @Override
    public void removeSection(int section) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        int cnt = getSectionRealItemCount(section);
        int start = getSectionFirstPos(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        if (adapterWrapper.getHeaderType() != SectionAdapter.NO_HEADER_TYPE) {
            updateHeaderTypeCnt(adapterWrapper.getHeaderType(), -1);
        }
        adapterWrapper.resetAdapter();
        typeToAdapter.remove(sectionType);
        typeToCallback.remove(sectionType);
        sectionToType.remove(section);
        sectionToPosSum.remove(section);
        updatePosSum(section, -cnt, true);
        adapter.notifyItemRangeRemoved(start, cnt);
        if (headerManager != null) {
            headerManager.checkFirstVisiblePos();
        }
    }

    @Override
    public void updateSection(int section) {
        checkSectionIndex(section);
        adapter.notifyItemRangeChanged(getSectionFirstPos(section), getSectionRealItemCount(section));
        if (headerManager != null) {
            short sectionType = sectionToType.get(section);
            headerManager.updateHeaderView(sectionType);
        }
    }

    @Override
    public void setSwipeCallback(int section, @NonNull SectionItemSwipeCallback swipeCallback) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        typeToCallback.put(sectionType, swipeCallback);
    }

    @Override
    public void removeSwipeCallback(int section) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        typeToCallback.remove(sectionType);
    }

    @Override
    public <T extends BaseSectionAdapter> T getSectionAdapter(int section) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        return adapterWrapper.getAdapter();
    }

    @Override
    public SectionItemSwipeCallback getSwipeCallback(int section) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        return typeToCallback.get(sectionType);
    }

    /* END SECTION MANAGER */
    /* ADAPTER */

    /**
     * This RecyclerView.Adapter implementation provides interaction between RecyclerView and
     * SectionAdapters.
     * <p>
     * Can be obtained by calling {@link #getAdapter()}.
     *
     * @see BaseSectionAdapter
     * @see SectionAdapter
     * @see BaseSectionAdapter.ItemViewHolder
     * @see BaseSectionAdapter.HeaderViewHolder
     */
    private RecyclerView.Adapter<ViewHolderWrapper> adapter = new RecyclerView.Adapter<ViewHolderWrapper>() {

        /**
         * Uses type to get an appropriate SectionAdapterWrapper, item type within section and to
         * determine, whether item view is a section header. Passes the corresponding call to the
         * BaseSectionAdapter via {@link SectionAdapterWrapper}, obtaining
         * {@link BaseSectionAdapter.ViewHolder}. Returns {@link ViewHolderWrapper}, that refers to the
         * same View. BaseSectionAdapter.ViewHolder holds a reference to it to access the global adapter
         * position any time.
         */
        @Override
        public ViewHolderWrapper onCreateViewHolder(ViewGroup parent, int type) {
            short sectionType = (short) (type);
            short itemType = (short) (type >> 16);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(Math.abs(sectionType));
            BaseSectionAdapter.ViewHolder viewHolder;
            if (isTypeHeader(sectionType)) {
                viewHolder = adapterWrapper.onCreateHeaderViewHolder(parent);
            } else {
                viewHolder = adapterWrapper.onCreateViewHolder(parent, itemType);
            }
            ViewHolderWrapper viewHolderWrapper = new ViewHolderWrapper(viewHolder);
            viewHolder.viewHolderWrapper = viewHolderWrapper;
            viewHolder.positionConverter = SectionsDataManager.this;
            return viewHolderWrapper;
        }

        /**
         * Uses position to determine section type and whether item view is a section header.
         * Obtains {@link BaseSectionAdapter.ViewHolder} from {@link ViewHolderWrapper} and passes the
         * corresponding call to the BaseSectionAdapter via {@link SectionAdapterWrapper}.
         */
        @Override
        public void onBindViewHolder(ViewHolderWrapper viewHolderWrapper, int position) {
            int type = getItemViewType(position);
            short sectionType = (short) (type);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(Math.abs(sectionType));
            if (isTypeHeader(sectionType)) {
                BaseSectionAdapter.HeaderViewHolder headerViewHolder = (BaseSectionAdapter.HeaderViewHolder) viewHolderWrapper.viewHolder;
                adapterWrapper.onBindHeaderViewHolder(headerViewHolder);
            } else {
                int sectionPos = calcPosInSection(position);
                BaseSectionAdapter.ItemViewHolder itemViewHolder = (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder;
                adapterWrapper.onBindViewHolder(itemViewHolder, sectionPos);
            }
        }

        @Override
        public int getItemCount() {
            return getTotalItemCount();
        }

        /**
         * Item view type allows to determine section type, item type within section and whether
         * item view is a section header. It is an integer, consisted of two shorts as follows:
         * <code>(itemType << 16) + sectionType</code>,
         * where <code>itemType</code> is an item type within section, obtained from
         * SectionAdapterWrapper, and <code>sectionType</code> is a section type, calculated from
         * adapter position, which is negative (multiplied by -1) when the given item view
         * corresponds to a section header.
         */
        @Override
        public int getItemViewType(int pos) {
            int section = calcSection(pos);
            short sectionType = sectionToType.get(section);
            int sectionPos = calcPosInSection(pos);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            short itemType = adapterWrapper.getItemViewType(sectionPos);
            if (adapterWrapper.isHeaderVisible() && getSectionFirstPos(section) == pos) sectionType *= -1;
            return (itemType << 16) + sectionType;
        }

    };

    /* END ADAPTER */
    /* SWIPE CALLBACK */

    /**
     * ItemTouchHelper.Callback implementation provides interaction between RecyclerView and
     * SectionItemSwipeCallbacks.
     * <p>
     * Can be obtained by calling {@link #getSwipeCallback()}.
     *
     * @see SectionItemSwipeCallback
     */
    private ItemTouchHelper.Callback swipeCallback = new ItemTouchHelper.Callback() {

        /**
         * Restricts dragging for all items and swiping for section headers. Returns swipe flags
         * obtained from corresponding {@link SectionItemSwipeCallback}, if it exists and the
         * swiping is enabled.
         */
        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int swipeFlags = 0;
            if (!isTypeHeader(viewHolder.getItemViewType())) {
                SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
                if (swipeCallback != null && swipeCallback.isSwipeEnabled()) {
                    ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                    swipeFlags = swipeCallback.getSwipeDirFlags(recyclerView, (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder);
                }
            }
            return makeMovementFlags(0, swipeFlags);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                              RecyclerView.ViewHolder target) {
            return false;
        }

        /**
         * Passes the corresponding call to the {@link SectionItemSwipeCallback}.
         */
        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
            if (swipeCallback != null) {
                ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                swipeCallback.onSwiped((BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder, direction);
            }
        }

        /**
         * Passes the corresponding call to the {@link SectionItemSwipeCallback}.
         */
        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (viewHolder.getAdapterPosition() >= 0) {
                SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
                if (swipeCallback != null) {
                    ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                    swipeCallback.onChildDraw(c, recyclerView,
                            (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder,
                            dX, dY, actionState, isCurrentlyActive);
                }
            } else {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        }

        /**
         * Passes the corresponding call to the {@link SectionItemSwipeCallback}.
         */
        @Override
        public void onChildDrawOver(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (viewHolder.getAdapterPosition() >= 0) {
                SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
                if (swipeCallback != null) {
                    ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                    swipeCallback.onChildDrawOver(c, recyclerView,
                            (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder,
                            dX, dY, actionState, isCurrentlyActive);
                }
            } else {
                super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        }

        /**
         * Passes the corresponding call to the {@link SectionItemSwipeCallback}.
         */
        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getAdapterPosition() >= 0) {
                SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
                if (swipeCallback != null) {
                    ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                    swipeCallback.clearView(recyclerView,
                            (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder);
                }
            } else {
                super.clearView(recyclerView, viewHolder);
            }
        }

        /**
         * Passes the corresponding call to the {@link SectionItemSwipeCallback}.
         */
        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder == null) return;
            if (viewHolder.getAdapterPosition() >= 0) {
                SectionItemSwipeCallback swipeCallback = getSwipeCallback(viewHolder);
                if (swipeCallback != null) {
                    ViewHolderWrapper viewHolderWrapper = (ViewHolderWrapper) viewHolder;
                    swipeCallback.onSelectedChanged(
                            (BaseSectionAdapter.ItemViewHolder) viewHolderWrapper.viewHolder, actionState);
                }
            } else {
                super.onSelectedChanged(viewHolder, actionState);
            }
        }

    };

    /* END SWIPE CALLBACK */
    /* SECTION ITEM MANAGER */

    private SectionItemManager sectionItemManager = new SectionItemManager() {

        @Override
        public void notifyInserted(int section, int pos) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, pos, true);
            checkSectionItemCntConsistency(section, 1);
            updatePosSum(section, 1, false);
            int adapterPos = getAdapterPos(section, pos);
            adapter.notifyItemInserted(adapterPos);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyRemoved(int section, int pos) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, pos);
            checkSectionItemCntConsistency(section, -1);
            updatePosSum(section, -1, false);
            int adapterPos = getAdapterPos(section, pos);
            adapter.notifyItemRemoved(adapterPos);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyChanged(int section, int pos) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, pos);
            int adapterPos = getAdapterPos(section, pos);
            adapter.notifyItemChanged(adapterPos);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyRangeInserted(int section, int startPos, int cnt) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, startPos, true);
            checkRangeItemCnt(cnt);
            checkSectionItemCntConsistency(section, cnt);
            updatePosSum(section, cnt, false);
            int adapterStartPos = getAdapterPos(section, startPos);
            adapter.notifyItemRangeInserted(adapterStartPos, cnt);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyRangeRemoved(int section, int startPos, int cnt) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, startPos);
            checkRangeItemCnt(cnt);
            checkRangeBounds(section, startPos, cnt);
            checkSectionItemCntConsistency(section, -cnt);
            int adapterStartPos = getAdapterPos(section, startPos);
            updatePosSum(section, -cnt, false);
            adapter.notifyItemRangeRemoved(adapterStartPos, cnt);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }

        }

        @Override
        public void notifyRangeChanged(int section, int startPos, int cnt) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, startPos);
            checkRangeItemCnt(cnt);
            checkRangeBounds(section, startPos, cnt);
            int adapterStartPos = getAdapterPos(section, startPos);
            adapter.notifyItemRangeChanged(adapterStartPos, cnt);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyMoved(int section, int fromPos, int toPos) {
            checkSectionIndex(section);
            checkSectionItemIndex(section, fromPos);
            checkSectionItemIndex(section, toPos);
            int adapterFromPos = getAdapterPos(section, fromPos);
            int adapterToPos = getAdapterPos(section, toPos);
            adapter.notifyItemMoved(adapterFromPos, adapterToPos);
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyHeaderChanged(int section) {
            checkSectionIndex(section);
            short sectionType = sectionToType.get(section);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            if (!adapterWrapper.isHeaderVisible()) return;
            int headerPos = getSectionFirstPos(section);
            adapter.notifyItemChanged(headerPos);
            if (headerManager != null) {
                headerManager.updateHeaderView(sectionType);
            }
        }

        @Override
        public void notifyHeaderVisibilityChanged(int section, boolean visible) {
            checkSectionIndex(section);
            if (visible) {
                updatePosSum(section, 1, false);
                adapter.notifyItemInserted(getSectionFirstPos(section));
            } else {
                updatePosSum(section, -1, false);
                adapter.notifyItemRemoved(getSectionFirstPos(section));
            }
            if (headerManager != null) {
                headerManager.checkFirstVisiblePos();
            }
        }

        @Override
        public void notifyHeaderPinnedStateChanged(int section, boolean pinned) {
            checkSectionIndex(section);
            if (headerManager != null) {
                headerManager.checkIsHeaderViewChanged();
            }
        }
    };

    /* END SECTION ITEM MANAGER */
    /* POSITION CONVERTER */

    @Override
    public int calcAdapterPos(int section, int pos) {
        try {
            checkSectionIndex(section);
            checkSectionItemIndex(section, pos);
            return getAdapterPos(section, pos);
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    @Override
    public int calcSection(int adapterPos) {
        if (!checkIndex(adapterPos, getTotalItemCount())) {
            return -1;
        }
        return upperBoundBinarySearch(sectionToPosSum, adapterPos);
    }

    @Override
    public int calcPosInSection(int adapterPos) {
        if (!checkIndex(adapterPos, getTotalItemCount())) {
            return -1;
        }
        int section = calcSection(adapterPos);
        short sectionType = sectionToType.get(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        return adapterPos - (section > 0 ? sectionToPosSum.get(section - 1) : 0)
                - adapterWrapper.getHeaderVisibilityInt();
    }

    /* END POSITION CONVERTER */
    /* HEADER MANAGER */

    /**
     * Manages header state.
     * <p>
     * It determines, which header view corresponds to the first visible adapter position,
     * and adds/removes/translates the header view via {@link #headerViewManager}.
     * <p>
     * Duplicated {@link BaseSectionAdapter.HeaderViewHolder}s for {@link SectionHeaderLayout} can
     * be obtained by calling {@link #getDuplicatedHeaderVH(short)}. Every {@link SectionAdapter}'s
     * header is associated with a header type. It indicates that different SectionAdapters can use
     * the same HeaderViewHolder. HeaderViewHolder for any header type is created only once, cached
     * and stored in {@link #typeToHeader}.
     * <p>
     * The contents of the current header view can be updated by rebinding the corresponding
     * {@link BaseSectionAdapter.HeaderViewHolder}.
     */
    class HeaderManager implements HeaderPosProvider {

        short topSectionType = -1;
        int topHeaderType = SectionAdapter.NO_HEADER_TYPE;

        HeaderViewManager headerViewManager;
        SparseArray<BaseSectionAdapter.HeaderViewHolder> typeToHeader;

        HeaderManager(HeaderViewManager headerViewManager) {
            this.headerViewManager = headerViewManager;
            typeToHeader = new SparseArray<>();
        }

        void removeSelf() {
            headerManager = null;
        }

        /* HEADER POSITION PROVIDER */

        @Override
        public int getHeaderAdapterPos(short sectionType) {
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            int section = adapterWrapper.getSection();
            return getSectionFirstPos(section);
        }

        /* END HEADER POSITION PROVIDER */

        /**
         * Checks, whether the header should be updated (added/removed/translated) based on the first
         * visible position (e.g. called after swipe). To update the contents of the corresponding
         * header BaseSectionAdapter.ViewHolder you should call {@link #updateHeaderView(short)}.
         * <p>
         * Interacts with header view via {@link HeaderViewManager}. Current header view section type
         * is stored in {@link #topSectionType}.
         */
        void checkIsHeaderViewChanged() {
            int topPos = headerViewManager.getFirstVisiblePos();
            if (!checkIndex(topPos, getTotalItemCount())) {
                removeHeaderView();
                return;
            }
            int section = calcSection(topPos);
            short sectionType = sectionToType.get(section);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            if (adapterWrapper.isHeaderVisible() && adapterWrapper.isHeaderPinned()) {
                if (sectionType == topSectionType) {
                    int nextHeaderPos = getSectionFirstPos(section + 1);
                    headerViewManager.translateHeaderView(nextHeaderPos);
                } else {
                    int headerType = adapterWrapper.getHeaderType();
                    if (headerType == topHeaderType) {
                        topSectionType = sectionType;
                        updateHeaderView(topSectionType);
                        int nextHeaderPos = getSectionFirstPos(section + 1);
                        headerViewManager.translateHeaderView(nextHeaderPos);
                    } else {
                        addHeaderView(section);
                    }
                }
            } else {
                removeHeaderView();
            }
        }

        /**
         * Makes {@link #headerViewManager} check first visible position.
         */
        void checkFirstVisiblePos() {
            headerViewManager.checkFirstVisiblePos();
        }

        /**
         * Notifies {@link HeaderViewManager} that the header view should be added, passing next header
         * position for calculations.
         *
         * @param section Index of the top section.
         */
        private void addHeaderView(int section) {
            topSectionType = sectionToType.get(section);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(topSectionType);
            topHeaderType = adapterWrapper.getHeaderType();
            BaseSectionAdapter.HeaderViewHolder headerViewHolder = getDuplicatedHeaderVH(topSectionType);
            headerViewHolder.sectionType = topSectionType;
            adapterWrapper.onBindHeaderViewHolder(headerViewHolder);
            int nextHeaderPos = getSectionFirstPos(section + 1);
            headerViewManager.addHeaderView(headerViewHolder.itemView, nextHeaderPos);
        }

        /**
         * Updates the contents of the duplicated header view if <code>sectionType</code> matches the
         * current {@link #topSectionType}.
         *
         * @param sectionType Type of the updated section.
         */
        private void updateHeaderView(short sectionType) {
            if (sectionType != topSectionType) return;
            BaseSectionAdapter.HeaderViewHolder headerViewHolder = getDuplicatedHeaderVH(sectionType);
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            headerViewHolder.sectionType = sectionType;
            adapterWrapper.onBindHeaderViewHolder(headerViewHolder);
        }

        /**
         * Notifies {@link HeaderViewManager} that the header view should be removed.
         */
        private void removeHeaderView() {
            if (topSectionType != -1) {
                headerViewManager.removeHeaderView();
                topSectionType = -1;
                topHeaderType = SectionAdapter.NO_HEADER_TYPE;
            }
        }

        /**
         * Returns HeaderViewHolder for the given section type. Creates one only if it doesn't exist
         * yet and stores it in {@link #typeToHeader}.
         *
         * @param sectionType Type of the section.
         * @return BaseSectionAdapter.ViewHolder of the header.
         */
        private BaseSectionAdapter.HeaderViewHolder getDuplicatedHeaderVH(short sectionType) {
            SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
            int headerType = adapterWrapper.getHeaderType();
            if (headerType == SectionAdapter.NO_HEADER_TYPE) {
                return null;
            }
            BaseSectionAdapter.HeaderViewHolder headerViewHolder = typeToHeader.get(headerType);
            if (headerViewHolder == null) {
                ViewGroup parent = headerViewManager.getHeaderViewParent();
                headerViewHolder = adapterWrapper.onCreateHeaderViewHolder(parent);
                headerViewHolder.sourcePositionProvider = this;
                headerViewHolder.positionConverter = SectionsDataManager.this;
                typeToHeader.put(headerType, headerViewHolder);
            }
            return headerViewHolder;
        }

    }

    /* END HEADER MANAGER */

    /**
     * Checks whether the given item view type corresponds to header view.
     *
     * @see #adapter's getItemViewType(int)
     * @param type Item view type.
     * @return True if the given type corresponds to header, false otherwise.
     */
    private boolean isTypeHeader(int type) {
        return type < 0;
    }

    /**
     * Returns the total number of items in RecyclerView, that is the sum of SectionAdapters item
     * counts and the number of headers.
     *
     * @return Total number of items in RecyclerView.
     */
    private int getTotalItemCount() {
        return getSectionCount() > 0 ? sectionToPosSum.get(getSectionCount() - 1) : 0;
    }

    /**
     * Returns SectionItemSwipeCallback for the given ViewHolder or null, if the obtained adapter
     * position is invalid.
     *
     * @param viewHolder ViewHolder of the swiped view.
     * @return Corresponding SectionItemSwipeCallback if exists, or null.
     */
    private SectionItemSwipeCallback getSwipeCallback(RecyclerView.ViewHolder viewHolder) {
        int adapterPos = viewHolder.getAdapterPosition();
        if (!checkIndex(adapterPos, getTotalItemCount())) {
            return null;
        }
        int section = calcSection(adapterPos);
        short sectionType = sectionToType.get(section);
        return typeToCallback.get(sectionType);
    }

    /**
     * Returns the adapter position of the item at position <code>pos</code> counting from the first
     * position corresponding to the given section (the given position can be bigger than section
     * item count).
     *
     * @param section Index of the section.
     * @param pos     Item position.
     * @return Adapter position corresponding to the given section and position.
     */
    private int getAdapterPos(int section, int pos) {
        checkSectionIndex(section);
        short sectionType = sectionToType.get(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        return (section > 0 ? sectionToPosSum.get(section - 1) : 0) + pos + adapterWrapper.getHeaderVisibilityInt();
    }

    /**
     * Returns the first global adapter position, that corresponds to the given section (can be
     * either item or header view).
     *
     * @param section Index of the section.
     * @return First global adapter position.
     */
    private int getSectionFirstPos(int section) {
        checkSectionIndex(section, true);
        return section > 0 ? sectionToPosSum.get(section - 1) : 0;
    }

    /**
     * Returns the number of items in RecyclerView, which currently correspond to the given section
     * index (including header if it is visible).
     *
     * @param section Index of the section.
     * @return Number of items.
     */
    private int getSectionRealItemCount(int section) {
        checkSectionIndex(section);
        return sectionToPosSum.get(section) - (section > 0 ? sectionToPosSum.get(section - 1) : 0);
    }

    /**
     * Returns the number of items in RecyclerView, which currently correspond to the items of
     * the given section (excluding header).
     *
     * @param section Index of the section.
     * @return Number of items.
     */
    private int getSectionItemCount(int section) {
        short sectionType = sectionToType.get(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        return getSectionRealItemCount(section) - adapterWrapper.getHeaderVisibilityInt();
    }

    /**
     * Updates by <code>val</code> count of the adapters with header associated with the given
     * header type.
     *
     * @param headerType Type of the header.
     * @param val        Value to update by.
     */
    private void updateHeaderTypeCnt(int headerType, int val) {
        int curCnt = headerTypeToCnt.get(headerType, 0);
        int newCnt = curCnt + val;
        if (newCnt > 0) {
            headerTypeToCnt.put(headerType, newCnt);
        } else {
            headerTypeToCnt.remove(headerType);
            if (headerManager != null) {
                headerManager.typeToHeader.remove(headerType);
            }
        }
    }

    /**
     * Updates by <code>cnt</code> items the partial sum array of item counts starting with section
     * <code>startSection</code>. If <code>updateSection</code> is true, updates the section indexes
     * of the corresponding adapters.
     *
     * @param startSection  First section index to be updated.
     * @param cnt           Value to be updated by.
     * @param updateSection True if the adapters section indexes should be updated.
     */
    private void updatePosSum(int startSection, int cnt, boolean updateSection) {
        for (int s = startSection; s < getSectionCount(); s++) {
            if (updateSection) {
                short sectionType = sectionToType.get(s);
                typeToAdapter.get(sectionType).setSection(s);
            }
            int prevSum = sectionToPosSum.get(s);
            sectionToPosSum.set(s, prevSum + cnt);
        }
    }

    /**
     * Upper bound binary search implementation. Finds the first position in the list, where the
     * value is greater than the given key. In other words, where the key value should be inserted
     * (after other equal ones).
     *
     * @param list List where to search.
     * @param key  Value to be found.
     * @return Upper bound binary search result position.
     */
    private static int upperBoundBinarySearch(List<Integer> list, int key) {
        int l = 0, r = list.size() - 1;
        while (true) {
            if (l == r) {
                if (key < list.get(l)) {
                    return l;
                } else {
                    return l + 1;
                }
            }
            if (l + 1 == r) {
                if (key < list.get(l)) {
                    return l;
                } else if (key < list.get(r)) {
                    return r;
                } else {
                    return r + 1;
                }
            }
            int m = (l + r) / 2;
            if (key < list.get(m)) {
                r = m;
            } else {
                l = m + 1;
            }
        }
    }

    /* CHECKS */

    /**
     * Checks whether the given <code>index</code> is greater than or equal to zero and less
     * than <code>limit</code>.
     *
     * @param index Number to check.
     * @param limit Right end of the half-interval.
     * @return True if the given number belongs to the half-interval [0, limit)
     */
    private boolean checkIndex(int index, int limit) {
        return 0 <= index && index < limit;
    }

    /**
     * Calls {@link #checkSectionIndex(int, boolean)} with default <code>border</code> value (false).
     */
    private void checkSectionIndex(int section) {
        checkSectionIndex(section, false);
    }

    /**
     * Raises an exception if the given <code>section</code> index is invalid. If <code>border</code>
     * is true, the given section index can be equal to the section count.
     *
     * @param section Index of the section.
     * @param border  True if the given section index can be equal to the section count.
     */
    private void checkSectionIndex(int section, boolean border) {
        int sectionCnt = getSectionCount();
        int limit = sectionCnt + (border ? 1 : 0);
        if (!checkIndex(section, limit)) {
            throw new IndexOutOfBoundsException("Section index " + section + " is out of range. " +
                    "Current section count is " + sectionCnt + ".");
        }
    }

    /**
     * Calls {@link #checkSectionItemIndex(int, int, boolean)} with default <code>border</code>
     * value (false).
     */
    private void checkSectionItemIndex(int section, int pos) {
        checkSectionItemIndex(section, pos, false);
    }

    /**
     * Raises an exception if the given item position <code>pos</code> in the given section is
     * invalid. If <code>border</code> is true, the given position can be equal to the item count.
     *
     * @param section Index of the section.
     * @param pos     Position to check.
     * @param border  True if the given position can be equal to the item count.
     */
    private void checkSectionItemIndex(int section, int pos, boolean border) {
        int itemCnt = getSectionItemCount(section);
        int limit = itemCnt + (border ? 1 : 0);
        if (!checkIndex(pos, limit)) {
            throw new IndexOutOfBoundsException("Item position " + pos + " in section " + section
                    + " is out of range. " + "Current section item count is " + itemCnt + ".");
        }
    }

    /**
     * Raises an exception if the item count returned from the corresponding section adapter doesn't
     * match the expected one.
     *
     * @param section Index of the section.
     * @param delta   Value on which the item count is being changed.
     */
    private void checkSectionItemCntConsistency(int section, int delta) {
        short sectionType = sectionToType.get(section);
        SectionAdapterWrapper adapterWrapper = typeToAdapter.get(sectionType);
        int shouldBe = getSectionItemCount(section) + delta;
        int found = adapterWrapper.getItemCount();
        if (shouldBe != found) {
            throw new RuntimeException("Inconsistency detected. Section item count should be "
                    + shouldBe + ", but BaseSectionAdapter returned " + found + ".");
        }
    }

    /**
     * Raises an exception if the given item count is negative.
     *
     * @param cnt Number to check;
     */
    private void checkRangeItemCnt(int cnt) {
        if (cnt < 0) {
            throw new IllegalArgumentException("Item count in range cannot be negative.");
        }
    }

    /**
     * Raises an exception if position count <code>cnt</code> starting from <code>startPos</code> is
     * out of range (more than the item count of the given section).
     *
     * @param section  Index of the section to check.
     * @param startPos Position in section to count from.
     * @param cnt      Number of items.
     */
    private void checkRangeBounds(int section, int startPos, int cnt) {
        int itemCnt = getSectionItemCount(section);
        int lastPos = startPos + cnt - 1;
        if (!checkIndex(lastPos, itemCnt)) {
            throw new IndexOutOfBoundsException("Position count " +  cnt + " starting from position "
                    + startPos + " is out of range. Current item count is " +  itemCnt + ".");
        }
    }

    /**
     * Raises an exception if the given header type equals to {@link SectionAdapter#NO_HEADER_TYPE}.
     *
     * @param headerType Type of the header to check.
     */
    private void checkHeaderType(int headerType) {
        if (headerType == SectionAdapter.NO_HEADER_TYPE) {
            throw new IllegalArgumentException("Header type cannot be equal to NO_HEADER_TYPE that is -1.");
        }
    }

    /**
     * Raises an exception when <code>freeType</code> exceeded {@link Short#MAX_VALUE} and overflowed.
     */
    private void checkFreeType() {
        if (freeType < 0) {
            throw new RuntimeException("Exceeded number of created sections, so there is no available section type.");
        }
    }

    /* END CHECKS */

}
