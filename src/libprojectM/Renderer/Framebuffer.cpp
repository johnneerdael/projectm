#include "Framebuffer.hpp"

namespace libprojectM {
namespace Renderer {

Framebuffer::Framebuffer()
{
    m_framebufferIds.resize(1);
    glGenFramebuffers(1, m_framebufferIds.data());
    m_attachments.emplace(0, AttachmentsPerSlot());
}

Framebuffer::Framebuffer(int framebufferCount)
{
    m_framebufferIds.resize(framebufferCount);
    glGenFramebuffers(framebufferCount, m_framebufferIds.data());
    for (int index = 0; index < framebufferCount; index++)
    {
        m_attachments.emplace(index, AttachmentsPerSlot());
    }
}

Framebuffer::~Framebuffer()
{
    if (!m_framebufferIds.empty())
    {
        // Delete attached textures first
        m_attachments.clear();

        glDeleteFramebuffers(static_cast<int>(m_framebufferIds.size()), m_framebufferIds.data());
        m_framebufferIds.clear();
    }
}

auto Framebuffer::Count() const -> int
{
    return static_cast<int>(m_framebufferIds.size());
}

void Framebuffer::Bind(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, m_framebufferIds.at(framebufferIndex));

    // Check framebuffer completeness to avoid GL_INVALID_FRAMEBUFFER_OPERATION errors
    // Use cached check for better performance
    GLenum status = CheckFramebufferStatusCached(framebufferIndex);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Log warning but don't fail - caller should handle GL errors gracefully
        // This can happen during texture resizing or preset transitions
    }

    m_readFramebuffer = m_drawFramebuffer = framebufferIndex;
}

void Framebuffer::BindRead(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    glBindFramebuffer(GL_READ_FRAMEBUFFER, m_framebufferIds.at(framebufferIndex));

    m_readFramebuffer = framebufferIndex;
}

void Framebuffer::BindDraw(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_framebufferIds.at(framebufferIndex));

    // Check framebuffer completeness for draw operations
    GLenum status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Incomplete framebuffer for drawing - this will cause GL_INVALID_FRAMEBUFFER_OPERATION
        // Log warning but continue - calling code should check GL errors
    }

    m_drawFramebuffer = framebufferIndex;
}

void Framebuffer::Unbind()
{
    // Android TV: Reset READ & DRAW binding states to prevent stale read bindings
    // affecting blits/resolves - use GL_FRAMEBUFFER to reset both
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

bool Framebuffer::SetSize(int width, int height)
{
    if (width == 0 || height == 0 ||
        (width == m_width && height == m_height))
    {
        return false;
    }

    m_width = width;
    m_height = height;

    for (auto& attachments : m_attachments)
    {
        Bind(attachments.first);
        for (auto& texture : attachments.second)
        {
            texture.second->SetSize(width, height);
            glFramebufferTexture2D(GL_FRAMEBUFFER, texture.first, GL_TEXTURE_2D, texture.second->Texture()->TextureID(), 0);
        }
        
        // Check framebuffer completeness after resizing all attachments
        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
        {
            // Framebuffer is incomplete after resize - this is a critical source of GL_INVALID_FRAMEBUFFER_OPERATION
            // This commonly happens during preset transitions when textures are being resized
        }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    // Invalidate cached framebuffer status after resize
    m_statusCacheValid = false;
    m_framebufferStatusCache.clear();

    return true;
}

auto Framebuffer::Width() const -> int
{
    return m_width;
}

auto Framebuffer::Height() const -> int
{
    return m_height;
}

auto Framebuffer::GetAttachment(int framebufferIndex, TextureAttachment::AttachmentType type, int attachmentIndex) const -> std::shared_ptr<TextureAttachment>
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return {};
    }

    const auto& framebufferAttachments = m_attachments.at(framebufferIndex);
    GLenum textureType{GL_COLOR_ATTACHMENT0};

    switch (type)
    {
        case TextureAttachment::AttachmentType::Color:
            textureType = GL_COLOR_ATTACHMENT0 + attachmentIndex;
            break;
        case TextureAttachment::AttachmentType::Depth:
            textureType = GL_DEPTH_ATTACHMENT;
            break;
        case TextureAttachment::AttachmentType::Stencil:
            textureType = GL_STENCIL_ATTACHMENT;
            break;
        case TextureAttachment::AttachmentType::DepthStencil:
            textureType = GL_DEPTH_STENCIL_ATTACHMENT;
            break;
    }

    if (framebufferAttachments.find(textureType) == framebufferAttachments.end()) {
        return {};
    }

    return framebufferAttachments.at(textureType);
}

void Framebuffer::SetAttachment(int framebufferIndex, int attachmentIndex, const std::shared_ptr<TextureAttachment>& attachment)
{
    if (!attachment)
    {
        return;
    }

    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    GLenum textureType{GL_COLOR_ATTACHMENT0};

    switch (attachment->Type())
    {
        case TextureAttachment::AttachmentType::Color:
            textureType = GL_COLOR_ATTACHMENT0 + attachmentIndex;
            break;
        case TextureAttachment::AttachmentType::Depth:
            textureType = GL_DEPTH_ATTACHMENT;
            break;
        case TextureAttachment::AttachmentType::Stencil:
            textureType = GL_STENCIL_ATTACHMENT;
            break;
        case TextureAttachment::AttachmentType::DepthStencil:
            textureType = GL_DEPTH_STENCIL_ATTACHMENT;
            break;
    }
    m_attachments.at(framebufferIndex).insert({textureType, attachment});

    glBindFramebuffer(GL_FRAMEBUFFER, m_framebufferIds.at(framebufferIndex));

    if (m_width > 0 && m_height > 0)
    {
        glFramebufferTexture2D(GL_FRAMEBUFFER, textureType, GL_TEXTURE_2D, attachment->Texture()->TextureID(), 0);
    }
    UpdateDrawBuffers(framebufferIndex);

    // Reset to previous read/draw buffers
    glBindFramebuffer(GL_READ_FRAMEBUFFER, m_framebufferIds.at(m_readFramebuffer));
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_framebufferIds.at(m_drawFramebuffer));
}

void Framebuffer::CreateColorAttachment(int framebufferIndex, int attachmentIndex)
{
    CreateColorAttachment(framebufferIndex, attachmentIndex, GL_RGBA, GL_RGBA, GL_UNSIGNED_BYTE);
}

void Framebuffer::CreateColorAttachment(int framebufferIndex, int attachmentIndex, GLint internalFormat, GLenum format, GLenum type)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    auto textureAttachment = std::make_shared<TextureAttachment>(internalFormat, format, type, m_width, m_height);
    const auto texture = textureAttachment->Texture();
    m_attachments.at(framebufferIndex).insert({GL_COLOR_ATTACHMENT0 + attachmentIndex, std::move(textureAttachment)});

    Bind(framebufferIndex);
    if (m_width > 0 && m_height > 0)
    {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + attachmentIndex, GL_TEXTURE_2D, texture->TextureID(), 0);
    }
    UpdateDrawBuffers(framebufferIndex);
    
    // Check framebuffer completeness after attaching color texture
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Framebuffer is incomplete after color attachment - this could cause GL_INVALID_FRAMEBUFFER_OPERATION
        // Continue anyway but calling code should handle GL errors gracefully
    }
    
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    // Invalidate cache since framebuffer attachments changed
    m_statusCacheValid = false;
}

void Framebuffer::RemoveColorAttachment(int framebufferIndex, int attachmentIndex)
{
    RemoveAttachment(framebufferIndex,  GL_COLOR_ATTACHMENT0 + attachmentIndex);
}

auto Framebuffer::GetColorAttachmentTexture(int framebufferIndex, int attachmentIndex) const -> std::shared_ptr<class Texture>
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return {};
    }

    const auto& attachment = m_attachments.at(framebufferIndex);
    if (attachment.find(GL_COLOR_ATTACHMENT0 + attachmentIndex) == attachment.end())
    {
        return {};
    }

    return attachment.at(GL_COLOR_ATTACHMENT0 + attachmentIndex)->Texture();
}

void Framebuffer::CreateDepthAttachment(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    auto textureAttachment = std::make_shared<TextureAttachment>(TextureAttachment::AttachmentType::Depth, m_width, m_height);
    const auto texture = textureAttachment->Texture();
    m_attachments.at(framebufferIndex).insert({GL_DEPTH_ATTACHMENT, std::move(textureAttachment)});

    Bind(framebufferIndex);
    if (m_width > 0 && m_height > 0)
    {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, texture->TextureID(), 0);
    }
    UpdateDrawBuffers(framebufferIndex);
    
    // Check framebuffer completeness after attaching depth texture
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Framebuffer is incomplete after depth attachment
    }
    
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    // Invalidate cache since framebuffer attachments changed
    m_statusCacheValid = false;
}

void Framebuffer::RemoveDepthAttachment(int framebufferIndex)
{
    RemoveAttachment(framebufferIndex, GL_DEPTH_ATTACHMENT);
}

void Framebuffer::CreateStencilAttachment(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    auto textureAttachment = std::make_shared<TextureAttachment>(TextureAttachment::AttachmentType::Stencil, m_width, m_height);
    const auto texture = textureAttachment->Texture();
    m_attachments.at(framebufferIndex).insert({GL_STENCIL_ATTACHMENT, std::move(textureAttachment)});

    Bind(framebufferIndex);
    if (m_width > 0 && m_height > 0)
    {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_TEXTURE_2D, texture->TextureID(), 0);
    }
    UpdateDrawBuffers(framebufferIndex);
    
    // Check framebuffer completeness after attaching stencil texture
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Framebuffer is incomplete after stencil attachment
    }
    
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    // Invalidate cache since framebuffer attachments changed
    m_statusCacheValid = false;
}

void Framebuffer::RemoveStencilAttachment(int framebufferIndex)
{
    RemoveAttachment(framebufferIndex, GL_STENCIL_ATTACHMENT);
}

void Framebuffer::CreateDepthStencilAttachment(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    auto textureAttachment = std::make_shared<TextureAttachment>(TextureAttachment::AttachmentType::DepthStencil, m_width, m_height);
    const auto texture = textureAttachment->Texture();
    m_attachments.at(framebufferIndex).insert({GL_DEPTH_STENCIL_ATTACHMENT, std::move(textureAttachment)});

    Bind(framebufferIndex);
    if (m_width > 0 && m_height > 0)
    {
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, texture->TextureID(), 0);
    }
    UpdateDrawBuffers(framebufferIndex);
    
    // Check framebuffer completeness after attaching depth-stencil texture
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE)
    {
        // Framebuffer is incomplete after depth-stencil attachment
    }
    
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    // Invalidate cache since framebuffer attachments changed
    m_statusCacheValid = false;
}

void Framebuffer::RemoveDepthStencilAttachment(int framebufferIndex)
{
    RemoveAttachment(framebufferIndex, GL_DEPTH_STENCIL_ATTACHMENT);
}

void Framebuffer::MaskDrawBuffer(int bufferIndex, bool masked)
{
#ifdef USE_GLES
    // bufferIndex is unused in OpenGL ES as there's only one color buffer
    (void)bufferIndex;
#endif
    // Invert the flag, as "true" means the color channel *will* be written.
    auto glMasked = static_cast<GLboolean>(!masked);
#ifdef USE_GLES
    glColorMask(glMasked, glMasked, glMasked, glMasked);
#else
    glColorMaski(bufferIndex, glMasked, glMasked, glMasked, glMasked);
#endif
}

void Framebuffer::UpdateDrawBuffers(int framebufferIndex)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    const auto& attachments = m_attachments.at(framebufferIndex);

    std::vector<GLenum> buffers;

    // Android TV: Only include COLOR attachments in draw buffers list
    // Never include GL_DEPTH_ATTACHMENT/GL_STENCIL_ATTACHMENT in glDrawBuffers
    for (const auto& attachment : attachments)
    {
        // Only add color attachments to draw buffers
        if (attachment.first >= GL_COLOR_ATTACHMENT0 && attachment.first <= GL_COLOR_ATTACHMENT31)
        {
            buffers.push_back(attachment.first);
        }
    }

    // Android TV: If no color targets, pass GL_NONE
    if (buffers.empty())
    {
        GLenum noneBuffer = GL_NONE;
        glDrawBuffers(1, &noneBuffer);
    }
    else
    {
        glDrawBuffers(static_cast<GLsizei>(buffers.size()), buffers.data());
    }
}

void Framebuffer::RemoveAttachment(int framebufferIndex, GLenum attachmentType)
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, m_framebufferIds.at(framebufferIndex));

    glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentType, GL_TEXTURE_2D, 0, 0);
    UpdateDrawBuffers(framebufferIndex);

    m_attachments.at(framebufferIndex).erase(attachmentType);

    // Reset to previous read/draw buffers
    glBindFramebuffer(GL_READ_FRAMEBUFFER, m_framebufferIds.at(m_readFramebuffer));
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_framebufferIds.at(m_drawFramebuffer));
}

GLenum Framebuffer::CheckFramebufferStatusCached(int framebufferIndex, GLenum target) const
{
    if (framebufferIndex < 0 || framebufferIndex >= static_cast<int>(m_framebufferIds.size()))
    {
        return GL_FRAMEBUFFER_UNDEFINED;
    }
    
    // Check cache first for performance optimization
    if (m_statusCacheValid && target == GL_FRAMEBUFFER)
    {
        auto it = m_framebufferStatusCache.find(framebufferIndex);
        if (it != m_framebufferStatusCache.end())
        {
            return it->second;
        }
    }
    
    // Cache miss or different target - perform actual GL check
    GLint currentFramebuffer;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFramebuffer);
    
    glBindFramebuffer(target, m_framebufferIds.at(framebufferIndex));
    GLenum status = glCheckFramebufferStatus(target);
    
    // Restore previous framebuffer binding
    glBindFramebuffer(target, currentFramebuffer);
    
    // Cache the result if it's a standard framebuffer check
    if (target == GL_FRAMEBUFFER)
    {
        m_framebufferStatusCache[framebufferIndex] = status;
        m_statusCacheValid = true;
    }
    
    return status;
}

} // namespace Renderer
} // namespace libprojectM
