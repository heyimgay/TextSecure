/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ResUtil;

import java.util.Locale;
import java.util.Set;

import static org.thoughtcrime.securesms.util.SpanUtil.color;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipients.RecipientsModifiedListener, Unbindable
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Set<Long>       selectedThreads;
  private Recipients      recipients;
  private long            threadId;
  private TextView        subjectView;
  private FromTextView    fromView;
  private TextView        dateView;
  private boolean         read;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;
  private ImageView       failedIndicator;
  private ImageView       deliveredIndicator;
  private ImageView       sentIndicator;
  private View            pendingIndicator;
  private ImageView       pendingApprovalIndicator;

  private StatusManager          statusManager;

  private final @DrawableRes int readBackground;
  private final @DrawableRes int unreadBackround;

  private final Handler handler = new Handler();
  private int distributionType;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    readBackground  = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_read);
    unreadBackround = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_unread);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    
    initializeAttributes();
    ViewGroup pendingIndicatorStub = (ViewGroup) findViewById(R.id.pending_indicator_stub);

    if (pendingIndicatorStub != null) {
      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      if (Build.VERSION.SDK_INT >= 11) inflater.inflate(R.layout.conversation_item_pending_v11, pendingIndicatorStub, true);
      else                             inflater.inflate(R.layout.conversation_item_pending, pendingIndicatorStub, true);
    }
    
    this.subjectView       = (TextView)        findViewById(R.id.subject);
    this.fromView          = (FromTextView)    findViewById(R.id.from);
    this.dateView          = (TextView)        findViewById(R.id.date);
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.thumbnailView     = (ThumbnailView)   findViewById(R.id.thumbnail);
    
    this.failedIndicator          = (ImageView)       findViewById(R.id.sms_failed_indicator);
    this.deliveredIndicator       = (ImageView)       findViewById(R.id.delivered_indicator);
    this.sentIndicator            = (ImageView)       findViewById(R.id.sent_indicator);
    this.pendingApprovalIndicator = (ImageView)       findViewById(R.id.pending_approval_indicator);
    this.pendingIndicator         =                   findViewById(R.id.pending_indicator);
    this.statusManager            = new StatusManager(pendingIndicator, sentIndicator, deliveredIndicator, failedIndicator, pendingApprovalIndicator);

    this.thumbnailView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ConversationListItem.this.performClick();
      }
    });
  }

  public void set(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                  @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();
    this.status           = thread.getStatus(); //TODO 

    this.recipients.addListener(this);
    this.fromView.setText(recipients, read);

    this.subjectView.setText(thread.getDisplayBody());
    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(read ? date : color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    setThumbnailSnippet(masterSecret, thread);
    setBatchState(batchMode);
    setBackground(thread);
    setRippleColor(recipients);
    this.contactPhotoImage.setAvatar(recipients, true);
  }

  @Override
  public void unbind() {
    if (this.recipients != null) this.recipients.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getDistributionType() {
    return distributionType;
  }

  private void setThumbnailSnippet(MasterSecret masterSecret, ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(masterSecret, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      this.subjectView.setLayoutParams(subjectParams);
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, 0);
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setBackground(ThreadRecord thread) {
    if (thread.isRead()) setBackgroundResource(readBackground);
    else                 setBackgroundResource(unreadBackround);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void setRippleColor(Recipients recipients) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipients.getColor().toConversationColor(getContext())));
    }
  }

  @Override
  public void onModified(final Recipients recipients) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipients, read);
        contactPhotoImage.setAvatar(recipients, true);
        setRippleColor(recipients);
      }
    });
  }
}

private void setStatusIcons(ThreadRecord threadRecord) {

    secureImage.setVisibility(threadRecord.isSecure() ? View.VISIBLE : View.GONE);
    bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, threadRecord.isKeyExchange() ? R.drawable.ic_menu_login : 0, 0);

    if      (threadRecord.isFailed())                     setFailedStatusIcons();
    else if (threadRecord.isPendingInsecureSmsFallback()) setFallbackStatusIcons();
    else if (threadRecord.isPending())                    statusManager.displayPending();
    else if (threadRecord.isDelivered())                  statusManager.displayDelivered();
    else                                                  statusManager.displaySent();
  }

private void setFailedStatusIcons() {
    statusManager.displayFailed();
    if (indicatorText != null) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

private void setFallbackStatusIcons() {
    statusManager.displayPendingApproval();
    indicatorText.setVisibility(View.VISIBLE);
    indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
  }
