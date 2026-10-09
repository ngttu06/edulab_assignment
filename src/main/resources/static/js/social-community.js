(() => {
    'use strict';
    const feed = document.getElementById('socialFeed');
    if (!feed || !window.edulab) return;
    const postRoot=feed.closest('.social-page');
    const {api, context} = window.edulab;
    const icon = name => {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('class','icon');svg.setAttribute('aria-hidden','true');svg.setAttribute('focusable','false');
        svg.setAttribute('width','24');svg.setAttribute('height','24');
        const use = document.createElementNS('http://www.w3.org/2000/svg','use');
        use.setAttribute('href',context+'/css/icons.svg#'+name);svg.append(use);return svg;
    };
    const el = (tag, cls, text) => {
        const item=document.createElement(tag);item.className=cls;
        if(text !== undefined) item.textContent=text;return item;
    };
    const action=(name,text)=>{ const button=el('button','',text);button.type='button';button.dataset.action=name;return button; };
    const endpoint=card=>'/api/v1/community/posts/'+card.dataset.id;
    const notice=(card,text)=>{card.querySelector('.social-post-notice').textContent=text;};
    const state=new WeakMap();
    const current=card=>{if(!state.has(card)) state.set(card,{after:null,loaded:false});return state.get(card);};
    const reactions={LIKE:'Thích',LOVE:'Yêu thích',CARE:'Thương thương',HAHA:'Haha',WOW:'Wow',SAD:'Buồn',ANGRY:'Phẫn nộ'};
    const reactionIcon=type=>icon('reaction-'+type.toLowerCase());
    function closeReactions(control,restore=false) {
        control.querySelector('.social-reaction-picker').hidden=true;
        const toggle=control.querySelector('[data-action=choose-reaction]');toggle.setAttribute('aria-expanded','false');
        if(restore) toggle.focus();
    }
    function openReactions(control,focus=false) {
        postRoot.querySelectorAll('.social-reaction-control').forEach(other=>{if(other!==control) closeReactions(other);});
        control.querySelector('.social-reaction-picker').hidden=false;
        control.querySelector('[data-action=choose-reaction]').setAttribute('aria-expanded','true');
        if(focus) (control.querySelector('[data-action=react][aria-pressed=true]') || control.querySelector('[data-action=react]')).focus();
    }
    function setupReactions(card) {
        const control=card.querySelector('.social-reaction-control'),toggle=action('choose-reaction','');
        toggle.append(icon('chevron-down'));toggle.setAttribute('aria-label','Chọn cảm xúc');toggle.setAttribute('title','Chọn cảm xúc');
        toggle.setAttribute('aria-expanded','false');toggle.setAttribute('aria-controls','socialReactions'+card.dataset.id);
        const picker=el('div','social-reaction-picker');picker.id='socialReactions'+card.dataset.id;picker.hidden=true;
        picker.setAttribute('role','group');picker.setAttribute('aria-label','Chọn cảm xúc cho bài viết');
        Object.entries(reactions).forEach(([type,label])=>{
            const button=action('react','');button.dataset.reaction=type;button.setAttribute('aria-label',label);button.title=label;
            button.setAttribute('aria-pressed',String(card.querySelector('[data-action=like]').dataset.reaction===type));
            button.append(reactionIcon(type),el('span','social-reaction-tooltip',label));picker.append(button);
        });control.append(toggle,picker);
        let timer;
        control.addEventListener('pointerenter',event=>{if(event.pointerType==='mouse') timer=setTimeout(()=>openReactions(control),250);});
        control.addEventListener('click',()=>clearTimeout(timer));
        control.addEventListener('pointerleave',()=>{clearTimeout(timer);if(!control.contains(document.activeElement)) closeReactions(control);});
        control.addEventListener('focusout',()=>setTimeout(()=>{if(!control.contains(document.activeElement)) closeReactions(control);},0));
        if(card.querySelector('.social-gallery a')) card.querySelectorAll('.social-gallery a,.social-post-time').forEach(link=>link.setAttribute('aria-haspopup','dialog'));
    }
    function updateReaction(card,result) {
        const type=reactions[result.reaction]?result.reaction:'',button=card.querySelector('[data-action=like]');
        button.dataset.reaction=type;button.setAttribute('aria-pressed',String(Boolean(type)));
        button.replaceChildren(type?reactionIcon(type):icon('like'),el('span','',reactions[type] || 'Thích'));
        card.querySelector('.social-like-count').textContent=result.count;
        const entries=Object.entries(result.counts || {}).filter(([key,count])=>reactions[key] && count>0).sort((a,b)=>b[1]-a[1] || a[0].localeCompare(b[0]));
        card.querySelector('.social-reaction-stack').replaceChildren(...entries.slice(0,3).map(([key])=>reactionIcon(key)));
        card.querySelector('.social-reaction-summary').title=entries.map(([key,count])=>reactions[key]+': '+count).join(' · ');
        card.querySelectorAll('[data-action=react]').forEach(option=>option.setAttribute('aria-pressed',String(option.dataset.reaction===type)));
    }

    function renderPost(post) {
        const card=el('article','social-surface social-post');card.dataset.id=post.id;
        const head=el('div','social-post-head'), avatar=el('span','social-avatar',Array.from(post.author || '')[0] || '');avatar.setAttribute('aria-hidden','true');
        const author=el('div','social-post-author'),time=el('a','social-post-time');
        time.href=context+'/community/posts/'+post.id;time.setAttribute('aria-label','Xem bài viết');
        time.append(el('time','',String(post.created_at)),document.createTextNode(' · '),icon('users'));
        author.append(el('strong','',post.author),time);
        const menu=el('details','social-post-menu'),toggle=el('summary',''),items=el('div','social-menu-items');
        toggle.setAttribute('aria-label','Tùy chọn bài viết');toggle.append(icon('more'));menu.append(toggle,items);
        if(post.mine) items.append(action('edit','Sửa bài viết'),action('delete','Xóa bài viết'));
        else items.append(action('report','Báo cáo bài viết'));
        if(feed.dataset.admin==='true') items.append(action('hide','Ẩn bài viết'));
        head.append(avatar,author,menu);card.append(head,el('p','social-post-text',post.body));
        if(post.images?.length) {
            const gallery=el('div','social-gallery'+(post.images.length===1?' single':''));
            gallery.dataset.count=post.images.length;
            post.images.forEach((image,index)=>{
                if(!image.image_ready && image.scan_status!=='CLEAN') {gallery.append(el('span','social-image-pending','Ảnh không khả dụng.'));return;}
                const link=el('a','');link.href=context+'/api/v1/files/'+image.id+'/image';link.target='_blank';link.rel='noopener';link.setAttribute('aria-label','Xem ảnh '+(index+1)+' trong bài viết');
                const img=el('img','');img.src=link.href;img.loading='lazy';img.alt='Ảnh '+(index+1)+' được chia sẻ trong bài viết';link.append(img);gallery.append(link);
            });card.append(gallery);
        }
        const counts=el('div','social-counts');
        const likes=el('span','social-reaction-summary'),reaction=el('span','social-reaction-stack');reaction.setAttribute('aria-hidden','true');likes.append(reaction);
        likes.append(el('span','social-like-count',String(post.like_count)),document.createTextNode(' cảm xúc'));
        const comments=action('comment-count','');comments.className='social-comment-total';comments.append(el('span','social-comment-count',String(post.comment_count)),document.createTextNode(' bình luận'));
        counts.append(likes,comments);card.append(counts);
        const actions=el('div','social-actions'), like=action('like',' Thích'),comment=action('comments',' Bình luận'),share=action('share',' Chia sẻ');
        like.prepend(icon('like'));like.setAttribute('aria-pressed',String(post.liked));comment.prepend(icon('chat'));share.prepend(icon('share'));
        comment.setAttribute('aria-expanded','false');comment.setAttribute('aria-controls','socialComments'+post.id);
        const control=el('div','social-reaction-control');control.append(like);actions.append(control,comment,share);card.append(actions);
        const message=el('p','social-post-notice');message.setAttribute('role','status');message.setAttribute('aria-live','polite');card.append(message);
        const section=el('section','social-comments');section.hidden=true;section.setAttribute('aria-label','Bình luận');
        section.id='socialComments'+post.id;
        const list=el('div','social-comment-list'),more=el('button','social-comment-more','Xem thêm bình luận');more.type='button';more.hidden=true;
        const form=el('form','social-comment-form'),label=el('label','visually-hidden','Viết bình luận');
        const input=el('textarea','');input.rows=1;input.id='socialComment'+post.id;label.htmlFor=input.id;input.required=true;input.maxLength=1000;input.placeholder='Viết bình luận...';
        input.setAttribute('aria-describedby','socialCommentHint'+post.id);
        const send=el('button','');send.type='submit';send.setAttribute('aria-label','Gửi bình luận');send.title='Gửi bình luận';send.append(icon('send'));
        const composeAvatar=el('span','social-avatar social-comment-avatar');composeAvatar.setAttribute('aria-hidden','true');composeAvatar.append(icon('user'));
        const compose=el('div','social-comment-compose'),field=el('div','social-comment-field'),hint=el('p','social-comment-hint','Enter để gửi · Shift + Enter để xuống dòng');
        hint.id='socialCommentHint'+post.id;field.append(input,send);compose.append(label,field,hint);form.append(composeAvatar,compose);
        const heading=el('div','social-comments-heading');heading.append(el('h3','','Bình luận'),el('span','','Cũ nhất trước'));
        const empty=el('p','social-comment-empty','Chưa có bình luận. Hãy bắt đầu cuộc trò chuyện!');empty.hidden=true;
        section.append(heading,list,empty,more,form);card.append(section);
        setupReactions(card);updateReaction(card,{reaction:post.reaction,count:post.reaction_count ?? post.like_count,counts:post.reaction_counts});
        return card;
    }
    function renderComment(comment,card) {
        const list=card.querySelector('.social-comment-list');
        if(Array.from(list.children).some(item=>item.dataset.id===String(comment.id))) return;
        const item=el('div','social-comment');item.dataset.id=comment.id;
        const avatar=el('span','social-avatar social-comment-avatar',Array.from(comment.author || '')[0] || '');avatar.setAttribute('aria-hidden','true');
        const content=el('div','social-comment-content'),bubble=el('div','social-comment-bubble'),meta=el('div','social-comment-meta');
        bubble.append(el('strong','',comment.author),el('p','social-comment-body',comment.body));
        const time=el('time','',String(comment.created_at)),date=new Date(comment.created_at);
        if(!Number.isNaN(date.getTime())) {time.dateTime=date.toISOString();time.textContent=new Intl.DateTimeFormat('vi-VN',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}).format(date);}
        meta.append(time);
        if(comment.mine) meta.append(action('edit-comment','Sửa'));
        if(comment.mine || feed.dataset.admin==='true') {const remove=action('delete-comment','Xóa');remove.setAttribute('aria-label','Xóa bình luận');meta.append(remove);}
        content.append(bubble,meta);item.append(avatar,content);
        const next=Array.from(list.children).find(child=>Number(child.dataset.id)>Number(comment.id));list.insertBefore(item,next || null);
        card.querySelector('.social-comment-empty').hidden=true;
    }
    async function loadComments(card) {
        const s=current(card),button=card.querySelector('.social-comment-more');if(s.loading)return;s.loading=true;button.disabled=true;
        card.querySelector('.social-comment-list').setAttribute('aria-busy','true');
        try {
            const result=await api(endpoint(card)+'/comments?size=10'+(s.after?'&after='+s.after:''));
            result.items.forEach(item=>renderComment(item,card));s.after=result.nextCursor;button.hidden=!result.hasMore;s.loaded=true;
            button.textContent='Xem thêm bình luận';
            card.querySelector('.social-comment-empty').hidden=Boolean(card.querySelector('.social-comment-list').children.length);
        } catch(error) {notice(card,error.message);button.hidden=false;button.textContent='Thử tải lại bình luận';}
        finally {button.disabled=false;s.loading=false;card.querySelector('.social-comment-list').setAttribute('aria-busy','false');}
    }
    function editComment(item,card) {
        if(item.querySelector('.social-comment-edit')) return;
        const body=item.querySelector('.social-comment-body'),form=el('form','social-comment-edit'),input=el('textarea','');
        input.value=body.textContent;input.maxLength=1000;input.required=true;input.rows=3;input.setAttribute('aria-label','Sửa bình luận');
        const save=el('button','','Lưu'),cancel=el('button','','Hủy');save.type='submit';cancel.type='button';
        const controls=el('div','');controls.append(save,cancel);form.append(input,controls);body.hidden=true;body.after(form);input.focus();
        const finish=()=>{form.remove();body.hidden=false;item.querySelector('[data-action=edit-comment]').focus();};
        cancel.addEventListener('click',finish);
        form.addEventListener('keydown',event=>{if(event.key==='Escape' && !save.disabled) {event.preventDefault();finish();}});
        form.addEventListener('submit',async event=>{
            event.preventDefault();if(save.disabled || !input.value.trim())return;save.disabled=true;cancel.disabled=true;input.readOnly=true;
            try {await api(endpoint(card)+'/comments/'+item.dataset.id,{method:'PUT',body:{body:input.value}});body.textContent=input.value.trim();finish();notice(card,'Đã sửa bình luận.');}
            catch(error) {notice(card,error.message);save.disabled=false;cancel.disabled=false;input.readOnly=false;input.focus();}
        });
    }
    const viewer=document.getElementById('socialPostViewer'),viewerImage=document.getElementById('socialViewerImage');
    const viewerDetails=document.getElementById('socialViewerDetails'),viewerThumbnails=document.getElementById('socialViewerThumbnails');
    const viewerStatus=document.getElementById('socialViewerImageStatus'),viewerCounter=document.getElementById('socialViewerCounter');
    const previousPhoto=document.getElementById('socialViewerPrevious'),nextPhoto=document.getElementById('socialViewerNext');
    let openedPost;
    function showPhoto(index) {
        if(!openedPost)return;
        openedPost.index=(index+openedPost.photos.length)%openedPost.photos.length;
        const photo=openedPost.photos[openedPost.index];
        viewerStatus.textContent='Đang tải ảnh…';viewerImage.src=photo.href;
        viewerImage.alt='Ảnh '+(openedPost.index+1)+' trong bài viết của '+openedPost.author;
        viewerCounter.textContent='Ảnh '+(openedPost.index+1)+' / '+openedPost.photos.length;
        viewerThumbnails.querySelectorAll('button').forEach((button,i)=>button.setAttribute('aria-pressed',String(i===openedPost.index)));
        previousPhoto.hidden=nextPhoto.hidden=openedPost.photos.length<2;
    }
    function openPost(card,trigger) {
        if(!viewer || viewer.open || openedPost)return;
        const photos=Array.from(card.querySelectorAll('.social-gallery a'));if(!photos.length)return;
        // Move, do not clone: preserve comment pages/drafts, reactions and unique IDs.
        const placeholder=el('div','social-viewer-placeholder');placeholder.style.height=card.getBoundingClientRect().height+'px';placeholder.setAttribute('aria-hidden','true');
        const comments=card.querySelector('.social-comments');
        openedPost={card,placeholder,photos,trigger,author:card.querySelector('.social-post-author strong').textContent,index:Math.max(0,photos.indexOf(trigger)),commentsHidden:comments.hidden,x:window.scrollX,y:window.scrollY};
        card.querySelectorAll('.social-post-menu').forEach(menu=>menu.open=false);
        closeReactions(card.querySelector('.social-reaction-control'));
        card.before(placeholder);viewerDetails.replaceChildren(card);comments.hidden=false;
        card.querySelector('[data-action=comments]').setAttribute('aria-expanded','true');
        document.getElementById('socialViewerTitle').textContent='Bài viết của '+openedPost.author;
        document.getElementById('socialViewerPermalink').href=context+'/community/posts/'+card.dataset.id;
        viewerThumbnails.replaceChildren(...photos.map((photo,index)=>{
            const button=el('button','social-viewer-thumb'),img=el('img','');button.type='button';
            button.setAttribute('aria-label','Xem ảnh '+(index+1));img.src=photo.href;img.alt='';button.append(img);
            button.addEventListener('click',()=>showPhoto(index));return button;
        }));
        document.documentElement.classList.add('social-viewer-open');viewer.showModal();viewerDetails.scrollTop=0;
        showPhoto(openedPost.index);document.getElementById('socialViewerClose').focus();
        if(!current(card).loaded) loadComments(card);
    }
    viewerImage?.addEventListener('load',()=>{viewerStatus.textContent='';});
    viewerImage?.addEventListener('error',()=>{viewerStatus.textContent='Không tải được ảnh. Ảnh có thể đã bị gỡ hoặc bạn không còn quyền xem.';});
    previousPhoto?.addEventListener('click',()=>showPhoto(openedPost.index-1));
    nextPhoto?.addEventListener('click',()=>showPhoto(openedPost.index+1));
    document.getElementById('socialViewerClose')?.addEventListener('click',()=>viewer.close());
    viewer?.addEventListener('click',event=>{
        const box=viewer.getBoundingClientRect();
        if(event.target===viewer && (event.clientX<box.left || event.clientX>box.right || event.clientY<box.top || event.clientY>box.bottom)) viewer.close();
    });
    viewer?.addEventListener('keydown',event=>{
        if(!openedPost || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey)return;
        if(event.target.closest('input,textarea,select,[contenteditable],.social-reaction-control'))return;
        if(event.key==='ArrowLeft' || event.key==='ArrowRight') {event.preventDefault();showPhoto(openedPost.index+(event.key==='ArrowRight'?1:-1));}
    });
    viewer?.addEventListener('close',()=>{
        if(!openedPost)return;
        const saved=openedPost;openedPost=null;
        if(saved.card.isConnected) {
            closeReactions(saved.card.querySelector('.social-reaction-control'));
            saved.card.querySelectorAll('.social-post-menu').forEach(menu=>menu.open=false);
            saved.card.querySelector('.social-comments').hidden=saved.commentsHidden;
            saved.card.querySelector('[data-action=comments]').setAttribute('aria-expanded',String(!saved.commentsHidden));
            saved.placeholder.replaceWith(saved.card);
        } else saved.placeholder.remove();
        viewerDetails.replaceChildren();viewerThumbnails.replaceChildren();viewerImage.removeAttribute('src');viewerStatus.textContent='';
        document.documentElement.classList.remove('social-viewer-open');window.scrollTo(saved.x,saved.y);
        if(saved.trigger.isConnected)saved.trigger.focus({preventScroll:true});else document.getElementById('mainContent').focus({preventScroll:true});
    });
    feed.querySelectorAll('.social-post').forEach(setupReactions);
    document.addEventListener('click',event=>{
        postRoot.querySelectorAll('.social-post-menu[open]').forEach(menu=>{if(!menu.contains(event.target)) menu.open=false;});
        postRoot.querySelectorAll('.social-reaction-control').forEach(control=>{if(!control.contains(event.target)) closeReactions(control);});
    });
    postRoot.addEventListener('keydown',event=>{
        const control=event.target.closest('.social-reaction-control');
        if(control && event.key==='Escape' && !control.querySelector('.social-reaction-picker').hidden) {closeReactions(control,true);event.preventDefault();return;}
        if(control && ['ArrowLeft','ArrowRight','Home','End'].includes(event.key) && event.target.matches('[data-action=react]')) {
            const options=Array.from(control.querySelectorAll('[data-action=react]')),index=options.indexOf(event.target);
            options[event.key==='Home'?0:event.key==='End'?6:(index+(event.key==='ArrowRight'?1:6))%7].focus();event.preventDefault();return;
        }
        if(event.target.matches('.social-comment-form textarea') && event.key==='Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();if(!event.target.readOnly) event.target.form.requestSubmit();return;
        }
        if(event.key!=='Escape') return;
        const menu=event.target.closest('.social-post-menu[open]');
        if(menu) {menu.open=false;menu.querySelector('summary').focus();event.preventDefault();}
    });
    postRoot.addEventListener('input',event=>{if(event.target.matches('.social-comment-form textarea')) {event.target.style.height='auto';event.target.style.height=Math.min(event.target.scrollHeight,160)+'px';}});
    postRoot.addEventListener('click',async event=>{
        const photoLink=event.target.closest('.social-gallery a,.social-post-time'),text=event.target.closest('.social-post-text');
        const photoCard=(photoLink || text)?.closest('.social-post');
        if(photoCard && !viewer?.open && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey && event.button===0 && window.getSelection()?.isCollapsed!==false && photoCard.querySelector('.social-gallery a')) {
            event.preventDefault();openPost(photoCard,photoLink || photoCard.querySelector('.social-post-time'));return;
        }
        const button=event.target.closest('[data-action],.social-comment-more');if(!button || !postRoot.contains(button)) return;
        const card=button.closest('.social-post');if(!card) return;
        const type=button.dataset.action;
        const menu=button.closest('.social-post-menu');if(menu) {menu.open=false;menu.querySelector('summary').focus();}
        if(button.classList.contains('social-comment-more')) {await loadComments(card);return;}
        if(type==='comments' || type==='comment-count') {
            const area=card.querySelector('.social-comments');area.hidden=viewer?.open || type==='comment-count'?false:!area.hidden;
            card.querySelector('[data-action=comments]').setAttribute('aria-expanded',String(!area.hidden));
            if(!area.hidden) {area.querySelector('textarea').focus();if(!current(card).loaded) await loadComments(card);}return;
        }
        if(type==='choose-reaction') {const control=button.closest('.social-reaction-control');if(button.getAttribute('aria-expanded')==='true') closeReactions(control);else openReactions(control,true);return;}
        if(type==='like' || type==='react') {
            const s=current(card);if(s.reacting)return;s.reacting=true;
            const control=button.closest('.social-reaction-control'),selected=card.querySelector('[data-action=like]').dataset.reaction;
            const typeToSave=type==='like'?(selected?'':'LIKE'):(selected===button.dataset.reaction?'':button.dataset.reaction);
            control.querySelectorAll('button').forEach(item=>item.disabled=true);closeReactions(control);
            try {const result=await api(endpoint(card)+'/reaction',{method:typeToSave?'PUT':'DELETE',...(typeToSave?{body:{reaction:typeToSave}}:{})});
                updateReaction(card,result);notice(card,result.reaction?'Đã bày tỏ cảm xúc '+reactions[result.reaction]+'.':'Đã gỡ cảm xúc.');
            } catch(error) {notice(card,error.message);}
            finally {s.reacting=false;control.querySelectorAll('button').forEach(item=>item.disabled=false);control.querySelector('[data-action=like]').focus();}return;
        }
        if(type==='share') {
            const url=location.origin+context+'/community/posts/'+card.dataset.id;
            try {await navigator.clipboard.writeText(url);notice(card,'Đã sao chép liên kết bài viết.');}
            catch {const input=el('input','');input.value=url;input.readOnly=true;input.setAttribute('aria-label','Liên kết bài viết');const message=card.querySelector('.social-post-notice');message.replaceChildren(document.createTextNode('Sao chép liên kết: '),input);input.focus();input.select();}return;
        }
        if(type==='delete-comment') {
            if(button.disabled)return;
            if(!confirm('Xóa bình luận này?')) return;
            button.disabled=true;
            try {await api(endpoint(card)+'/comments/'+button.closest('.social-comment').dataset.id,{method:'DELETE'});button.closest('.social-comment').remove();const count=card.querySelector('.social-comment-count');count.textContent=Math.max(0,Number(count.textContent)-1);
                card.querySelector('.social-comment-empty').hidden=Number(count.textContent)>0;card.querySelector('.social-comment-form textarea').focus();notice(card,'Đã xóa bình luận.');
            }catch(error){notice(card,error.message);}finally{button.disabled=false;}return;
        }
        if(type==='edit-comment') {editComment(button.closest('.social-comment'),card);return;}
        if((type==='delete' || type==='hide') && !confirm(type==='hide'?'Ẩn bài viết này?':'Xóa bài viết này?')) return;
        let input;
        if(type==='edit') {input=prompt('Chỉnh sửa nội dung bài viết:',card.querySelector('.social-post-text').textContent);if(input===null)return;}
        if(type==='report') {input=prompt('Vì sao bạn muốn báo cáo bài này?');if(!input?.trim())return;}
        button.disabled=true;
        try {
            if(type==='edit') {await api(endpoint(card),{method:'PUT',body:{body:input}});card.querySelector('.social-post-text').textContent=input.trim();notice(card,'Đã sửa bài viết.');}
            if(type==='delete' || type==='hide') {await api(endpoint(card)+(type==='hide'?'/hide':''),{method:type==='hide'?'POST':'DELETE'});card.remove();if(openedPost?.card===card)viewer.close();}
            if(type==='report') {await api(endpoint(card)+'/reports',{method:'POST',body:{reason:input}});notice(card,'Đã gửi báo cáo. Cảm ơn bạn!');}
        } catch(error) {notice(card,error.message);} finally {button.disabled=false;}
    });
    postRoot.addEventListener('submit',async event=>{
        const form=event.target.closest('.social-comment-form');if(!form)return;event.preventDefault();
        const card=form.closest('.social-post'),button=form.querySelector('button'),input=form.querySelector('textarea');
        if(button.disabled)return;
        if(!input.value.trim()) {notice(card,'Nhập nội dung bình luận trước khi gửi.');input.focus();return;}
        button.disabled=true;input.readOnly=true;
        try {const result=await api(endpoint(card)+'/comments',{method:'POST',body:{body:input.value}});input.value='';input.style.height='auto';
            card.querySelector('.social-comment-count').textContent=Number(card.querySelector('.social-comment-count').textContent)+1;
            // Render the server response safely; preserve loaded pages and the pending cursor.
            renderComment(result,card);
            notice(card,'Đã gửi bình luận.');
        } catch(error){notice(card,error.message);} finally {button.disabled=false;input.readOnly=false;input.focus();}
    });
    const more=document.getElementById('socialMore'),feedNotice=document.getElementById('socialFeedNotice');
    more?.addEventListener('click',async()=>{
        if(!feed.dataset.cursor)return;more.disabled=true;
        try {const params=new URLSearchParams({before:feed.dataset.cursor,mine:feed.dataset.mine});const result=await api('/api/v1/community/posts?'+params);
            result.items.forEach(item=>feed.append(renderPost(item)));feed.dataset.cursor=result.nextCursor||'';more.hidden=!result.hasMore;feedNotice.textContent=result.hasMore?'Đã tải thêm bài viết.':'Bạn đã xem hết bài viết.';
        }catch(error){feedNotice.textContent=error.message;}finally{more.disabled=false;}
    });

    const form=document.getElementById('socialForm');if(!form)return;
    const input=document.getElementById('socialFiles'),preview=document.getElementById('socialPreview'),status=document.getElementById('socialUploadStatus');
    const publish=document.getElementById('socialPublish'),cancel=document.getElementById('socialCancelUpload');
    let selected=[],uploadController;
    function clear() {selected.forEach(item=>URL.revokeObjectURL(item.url));selected=[];preview.replaceChildren();input.value='';}
    function draw() {
        preview.replaceChildren();selected.forEach((entry,index)=>{const box=el('div',''),img=el('img','');img.src=entry.url;img.alt='Ảnh '+(index+1)+' sẽ đăng';
            const remove=el('button','','×');remove.type='button';remove.setAttribute('aria-label','Bỏ ảnh '+(index+1));remove.addEventListener('click',()=>{if(publish.disabled)return;URL.revokeObjectURL(entry.url);selected.splice(index,1);draw();});
            box.append(img,remove);preview.append(box);
        });
    }
    function decode(file) {
        return new Promise((resolve,reject)=>{
            const img=new Image(),url=URL.createObjectURL(file);img.onload=()=>{URL.revokeObjectURL(url);resolve(img);};
            img.onerror=()=>{URL.revokeObjectURL(url);reject(new Error('Không đọc được ảnh. Hãy chọn JPG, PNG hoặc WebP hợp lệ.'));};img.src=url;
        });
    }
    function jpeg(canvas,quality) {return new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',quality));}
    async function withoutMetadata(blob) {
        // Canvas encoders may add ICC APP2 segments. Keep only JFIF and image data,
        // so browser JPEGs satisfy the same metadata-free policy as server checks.
        const bytes=new Uint8Array(await blob.arrayBuffer()),parts=[bytes.subarray(0,2)];
        if(bytes.length<4 || bytes[0]!==0xff || bytes[1]!==0xd8) throw new Error('Không xử lý được ảnh. Hãy chọn ảnh khác.');
        let pos=2;
        while(pos+3<bytes.length && bytes[pos]===0xff) {
            const marker=bytes[pos+1];
            if(marker===0xda) {parts.push(bytes.subarray(pos));return new Blob(parts,{type:'image/jpeg'});}
            const length=(bytes[pos+2]<<8)|bytes[pos+3],end=pos+2+length;
            if(length<2 || end>bytes.length) break;
            const jfif=marker===0xe0 && length>=7 && bytes[pos+4]===74 && bytes[pos+5]===70 && bytes[pos+6]===73 && bytes[pos+7]===70 && bytes[pos+8]===0;
            if(jfif || (marker<0xe0 || marker>0xef) && marker!==0xfe) parts.push(bytes.subarray(pos,end));
            pos=end;
        }
        throw new Error('Không xử lý được ảnh. Hãy chọn ảnh khác.');
    }
    async function compress(file) {
        if(!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size>20*1024*1024 || file.size<1) throw new Error('Chỉ chọn ảnh JPG/PNG/WebP dưới 20 MB.');
        const image=await decode(file);if(!image.naturalWidth || !image.naturalHeight || image.naturalWidth*image.naturalHeight>50_000_000) throw new Error('Kích thước ảnh quá lớn.');
        let edge=Math.min(1600,Math.max(image.naturalWidth,image.naturalHeight)),blob;
        while(edge>0) {
            const scale=Math.min(1,edge/Math.max(image.naturalWidth,image.naturalHeight));
            const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(image.naturalWidth*scale));canvas.height=Math.max(1,Math.round(image.naturalHeight*scale));
            const ctx=canvas.getContext('2d');if(!ctx) throw new Error('Trình duyệt không thể xử lý ảnh.');
            ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(image,0,0,canvas.width,canvas.height);
            for(const quality of [.78,.66,.54,.43]) {blob=await jpeg(canvas,quality);if(blob && blob.size<=300*1024) break;}
            canvas.width=0;canvas.height=0;
            if(blob && blob.size<=300*1024) break;
            if(edge<=320) break;
            edge=Math.max(320,Math.floor(edge*.75));
        }
        if(blob) blob=await withoutMetadata(blob);
        if(!blob || blob.size>500*1024 || blob.size<1) throw new Error('Không xử lý được ảnh. Hãy chọn ảnh khác.');
        return new File([blob],'community-'+crypto.randomUUID()+'.jpg',{type:'image/jpeg'});
    }
    input.addEventListener('change',async()=>{
        const files=Array.from(input.files);input.value='';
        if(selected.length+files.length>5){status.textContent='Chỉ chọn tối đa 5 ảnh.';return;}
        input.disabled=true;publish.disabled=true;
        try{for(const file of files){status.textContent='Đang chuẩn bị ảnh...';const compressed=await compress(file);
            selected.push({file:compressed,url:URL.createObjectURL(compressed)});draw();status.textContent='';
        }}catch(error){status.textContent=error.message;}finally{input.disabled=false;publish.disabled=false;}
    });
    cancel.addEventListener('click',()=>uploadController?.abort());
    form.addEventListener('submit',async event=>{
        event.preventDefault();if(publish.disabled)return;const body=document.getElementById('socialBody').value;
        if(!body.trim() && !selected.length){status.textContent='Nhập nội dung hoặc chọn ít nhất một ảnh.';return;}
        publish.disabled=true;input.disabled=true;cancel.hidden=false;uploadController=new AbortController();
        try {const ids=[];
            for(const entry of selected){if(uploadController.signal.aborted) throw new Error('Đã hủy upload.');
                if(entry.id){ids.push(entry.id);continue;}
                const file=entry.file,ticket=await api('/api/v1/files/community/presign',{method:'POST',body:{filename:file.name,contentType:file.type,size:file.size}});
                try{await window.edulabUploads.put(ticket.url,file,ticket.headers,p=>status.textContent='Đang tải ảnh: '+p+'%',uploadController.signal);
                    if(uploadController.signal.aborted) throw new Error('Đã hủy upload.');
                    const result=await api('/api/v1/files/uploads/'+ticket.uploadId+'/complete',{method:'POST'});
                    if(!result.fileId || !result.communityReady) throw new Error(result.message || 'Ảnh không hợp lệ cho Cộng đồng.');
                    entry.id=result.fileId;ids.push(entry.id);
                }catch(error){await api('/api/v1/files/uploads/'+ticket.uploadId,{method:'DELETE'}).catch(()=>{});throw error;}
            }
            if(uploadController.signal.aborted) throw new Error('Đã hủy upload.');
            const result=await api('/api/v1/community/posts',{method:'POST',body:{body,imageIds:ids}});
            document.getElementById('socialBody').value='';clear();status.textContent='Đã đăng bài viết!';
            try{const post=await api('/api/v1/community/posts/'+result.id);feed.prepend(renderPost(post));document.getElementById('socialEmpty')?.remove();}
            catch{status.textContent='Bài đã đăng thành công. Tải lại trang để xem bài viết.';}
        }catch(error){status.textContent=error.message;}finally{publish.disabled=false;input.disabled=false;cancel.hidden=true;uploadController=null;}
    });
})();